package com.liveklass.enrollment.concurrency;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.enrollment.application.EnrollmentService;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.infrastructure.EnrollmentRepository;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.payment.infrastructure.PaymentIntentRepository;
import com.liveklass.enrollment.user.domain.User;
import com.liveklass.enrollment.user.domain.UserRole;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import com.liveklass.enrollment.waitlist.infrastructure.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 명세 §3 "동시에 여러 사람이 마지막 자리에 신청하는 경우" 검증 — 실제 PostgreSQL(Testcontainers) 위에서.
 * 비관 락(SELECT ... FOR UPDATE on Lecture) + 부분 UNIQUE 인덱스가 정원을 정확히 지키는지 확인한다.
 * (Docker 가 실행 중이어야 한다.)
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true) // Docker 가 없으면 이 클래스 전체를 건너뜀(실패 아님)
@DisplayName("동시성 — 마지막 자리 동시 신청")
class ConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int THREADS = 50;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired EnrollmentService enrollmentService;
    @Autowired UserRepository userRepository;
    @Autowired LectureRepository lectureRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired WaitlistRepository waitlistRepository;
    @Autowired PaymentIntentRepository paymentIntentRepository;

    private Long lectureId;
    private List<Long> classmateIds;

    @BeforeEach
    void setUp() {
        paymentIntentRepository.deleteAll();
        enrollmentRepository.deleteAll();
        waitlistRepository.deleteAll();
        lectureRepository.deleteAll();
        userRepository.deleteAll();

        User creator = userRepository.save(new User("크리에이터", UserRole.CREATOR));
        Lecture lecture = new Lecture(creator.getId(), "동시성 테스트 강의", "정원 경쟁",
            new BigDecimal("10000"), CAPACITY, LocalDate.now().plusDays(1), LocalDate.now().plusDays(30));
        lecture.changeStatus(LectureStatus.OPEN);
        this.lectureId = lectureRepository.save(lecture).getId();

        this.classmateIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            classmateIds.add(userRepository.save(new User("학생" + i, UserRole.CLASSMATE)).getId());
        }
    }

    @Test
    @DisplayName("정원 3 강의에 50명이 동시에 신청하면 정확히 3명만 성공, 나머지는 CAPACITY_EXCEEDED")
    void onlyCapacityManySucceed() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger capacityExceeded = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (Long classmateId : classmateIds) {
            pool.submit(() -> {
                try {
                    start.await();
                    enrollmentService.apply(classmateId, lectureId);
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.CAPACITY_EXCEEDED) {
                        capacityExceeded.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(CAPACITY);
        assertThat(capacityExceeded.get()).isEqualTo(THREADS - CAPACITY);
        assertThat(unexpected.get()).isZero();

        // 정합성 sanity: enrolled_count == COUNT(active enrollments) == CAPACITY
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow();
        long activeCount = enrollmentRepository.findByLectureId(lectureId, Pageable.unpaged()).getContent().stream()
            .filter(e -> e.getStatus().isActive())
            .count();
        assertThat(lecture.getEnrolledCount()).isEqualTo(CAPACITY);
        assertThat(activeCount).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 같은 강의에 여러 번 신청해도 active 신청은 정확히 1개")
    void sameUserConcurrentApplyDeduplicated() throws InterruptedException {
        Long userId = classmateIds.get(0);
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    enrollmentService.apply(userId, lectureId);
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // DUPLICATE_ENROLLMENT 또는 DB 제약 위반 — 기대된 결과
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);

        List<Enrollment> mine = enrollmentRepository.findByLectureId(lectureId, Pageable.unpaged()).getContent().stream()
            .filter(e -> e.getUserId().equals(userId) && e.getStatus().isActive())
            .toList();
        assertThat(mine).hasSize(1);
        assertThat(lectureRepository.findById(lectureId).orElseThrow().getEnrolledCount()).isEqualTo(1);
    }
}
