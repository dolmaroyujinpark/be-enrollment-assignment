package com.liveklass.enrollment.lecture.application;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.lecture.presentation.dto.CreateLectureRequest;
import com.liveklass.enrollment.user.domain.User;
import com.liveklass.enrollment.user.domain.UserRole;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

    @Mock
    LectureRepository lectureRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    LectureService lectureService;

    private static User userWithId(Long id, UserRole role) {
        User user = new User("테스터", role);
        injectId(user, id);
        return user;
    }

    private static Lecture lectureWithId(Long id, Long creatorId) {
        Lecture lecture = new Lecture(
            creatorId,
            "Spring Boot 백엔드",
            "JPA · 트랜잭션 · 동시성",
            new BigDecimal("199000"),
            10,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1)
        );
        injectId(lecture, id);
        return lecture;
    }

    private static void injectId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CreateLectureRequest sampleRequest() {
        return new CreateLectureRequest(
            "신규 강의",
            "설명",
            new BigDecimal("99000"),
            5,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1)
        );
    }

    @Test
    @DisplayName("CREATOR 역할이면 강의가 저장된다")
    void register_creator_succeeds() {
        User creator = userWithId(10L, UserRole.CREATOR);
        given(userRepository.findById(10L)).willReturn(Optional.of(creator));
        given(lectureRepository.save(any(Lecture.class))).willAnswer(inv -> inv.getArgument(0));

        Lecture saved = lectureService.register(10L, sampleRequest());

        assertThat(saved.getCreatorId()).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(LectureStatus.DRAFT);
    }

    @Test
    @DisplayName("존재하지 않는 사용자가 등록 시도 시 IllegalArgumentException")
    void register_unknownUser_throws() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> lectureService.register(99L, sampleRequest()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CLASSMATE 가 강의 등록 시도 시 IllegalStateException")
    void register_classmate_throws() {
        User classmate = userWithId(20L, UserRole.CLASSMATE);
        given(userRepository.findById(20L)).willReturn(Optional.of(classmate));

        assertThatThrownBy(() -> lectureService.register(20L, sampleRequest()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CREATOR");
    }

    @Test
    @DisplayName("status 필터가 null 이면 전체 목록을 반환")
    void findAll_noFilter_returnsAll() {
        Lecture l1 = lectureWithId(1L, 10L);
        Lecture l2 = lectureWithId(2L, 10L);
        given(lectureRepository.findAllByOrderByIdDesc()).willReturn(List.of(l2, l1));

        List<Lecture> result = lectureService.findAll(null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("status 필터가 있으면 해당 상태만 반환")
    void findAll_withFilter_filtersByStatus() {
        Lecture l = lectureWithId(1L, 10L);
        l.changeStatus(LectureStatus.OPEN);
        given(lectureRepository.findAllByStatusOrderByIdDesc(LectureStatus.OPEN))
            .willReturn(List.of(l));

        List<Lecture> result = lectureService.findAll(LectureStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LectureStatus.OPEN);
    }

    @Test
    @DisplayName("findById 가 없으면 IllegalArgumentException")
    void findById_notFound_throws() {
        given(lectureRepository.findById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> lectureService.findById(404L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작성자 본인이 changeStatus 호출 시 전이 성공")
    void changeStatus_byCreator_succeeds() {
        Lecture lecture = lectureWithId(1L, 10L);
        given(lectureRepository.findById(1L)).willReturn(Optional.of(lecture));

        Lecture updated = lectureService.changeStatus(1L, LectureStatus.OPEN, 10L);

        assertThat(updated.getStatus()).isEqualTo(LectureStatus.OPEN);
    }

    @Test
    @DisplayName("작성자가 아닌 사용자가 changeStatus 호출 시 IllegalStateException")
    void changeStatus_byOther_throws() {
        Lecture lecture = lectureWithId(1L, 10L);
        given(lectureRepository.findById(1L)).willReturn(Optional.of(lecture));

        assertThatThrownBy(() -> lectureService.changeStatus(1L, LectureStatus.OPEN, 99L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("작성자");
    }
}
