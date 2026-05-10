package com.liveklass.enrollment.seed;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.user.domain.User;
import com.liveklass.enrollment.user.domain.UserRole;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
@Slf4j
public class SeedRunner implements CommandLineRunner {

    private static final long RANDOM_SEED = 42L;

    private static final List<String> SURNAMES = List.of(
        "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
        "한", "오", "서", "신", "권", "황", "안", "송", "전", "홍"
    );

    private static final List<String> GIVEN_NAMES = List.of(
        "도연", "재훈", "수민", "지호", "나연", "태경", "예린", "하준", "서영", "민재",
        "유진", "지원", "연우", "다은", "현우", "수아", "준영", "하늘", "지민", "은서",
        "주원", "수빈", "예준", "하영", "도현", "지안", "시우", "민서", "지유", "건우"
    );

    private static final List<String> CREATOR_TITLES = List.of(
        "크리에이터", "강사", "튜터", "선생님", "코치"
    );

    private static final List<LectureTemplate> LECTURE_TEMPLATES = List.of(
        new LectureTemplate("파이썬 입문", "기초 문법부터 자동화 스크립트까지. 매주 과제 + 라이브 Q&A.", 129000, 4),
        new LectureTemplate("자바스크립트 한 달 완성", "ES2024 기준. 비동기/모듈/타입 추론까지.", 159000, 6),
        new LectureTemplate("React로 만드는 첫 SPA", "useState/useEffect 기초부터 라우팅·전역상태까지.", 189000, 4),
        new LectureTemplate("Spring Boot 백엔드 부트캠프", "JPA·트랜잭션·동시성 제어 실무 패턴.", 249000, 8),
        new LectureTemplate("Figma로 시작하는 UX 설계", "신입 디자이너를 위한 Figma 풀 스택 코스.", 99000, 4),
        new LectureTemplate("Notion 업무 자동화", "데이터베이스·릴레이션·자동화 버튼 200% 활용.", 79000, 4),
        new LectureTemplate("브랜딩 글쓰기 워크숍", "톤앤매너·내러티브·SEO 라이팅까지.", 119000, 6),
        new LectureTemplate("아이폰 사진 마스터", "구도·노출·라이트룸 모바일 보정.", 89000, 4),
        new LectureTemplate("자동화 매크로 with Make.com", "Webhook·반복 일정·구글 스프레드 연동.", 149000, 6),
        new LectureTemplate("데이터 분석 입문 (Python)", "pandas·matplotlib로 실데이터 분석.", 199000, 8),
        new LectureTemplate("프로덕트 매니저 실무", "PRD·사용자 인터뷰·KPI 트리.", 229000, 6),
        new LectureTemplate("UX 라이팅 실전", "마이크로카피·온보딩 플로우·실패 메시지.", 109000, 4),
        new LectureTemplate("AI 프롬프트 엔지니어링", "Claude·GPT 비교, 도구 호출, 프롬프트 평가.", 169000, 4),
        new LectureTemplate("Tailwind CSS 핸즈온", "디자인 토큰·다크모드·컴포넌트 패턴.", 119000, 4),
        new LectureTemplate("일러스트 캐릭터 드로잉", "iPad + Procreate 입문~심화.", 139000, 6),
        new LectureTemplate("창업 BM 캔버스 워크샵", "린 스타트업·고객 검증·MVP 정의.", 199000, 4),
        new LectureTemplate("음성 콘텐츠 제작", "팟캐스트 기획·녹음·편집·배포.", 149000, 6),
        new LectureTemplate("Docker 입문 한 권", "이미지·볼륨·Compose·운영 베스트.", 129000, 4),
        new LectureTemplate("기술 블로그 글쓰기", "구조·태그·검색 최적화·꾸준함 전략.", 79000, 4),
        new LectureTemplate("모각코 클럽", "주 2회 라이브 코딩 모임. 진행 중.", 49000, 8)
    );

    private final UserRepository userRepository;
    private final LectureRepository lectureRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Seed: 데이터가 이미 존재해 시드를 건너뜀 (users={}, lectures={})",
                userRepository.count(), lectureRepository.count());
            return;
        }

        Random random = new Random(RANDOM_SEED);

        List<User> creators = seedCreators(random);
        List<User> classmates = seedClassmates(random);
        List<Lecture> lectures = seedLectures(creators, random);

        log.info("Seed 완료: users={} (CREATOR={}, CLASSMATE={}), lectures={} (DRAFT={}, OPEN={}, CLOSED={})",
            creators.size() + classmates.size(),
            creators.size(),
            classmates.size(),
            lectures.size(),
            countByStatus(lectures, LectureStatus.DRAFT),
            countByStatus(lectures, LectureStatus.OPEN),
            countByStatus(lectures, LectureStatus.CLOSED));
    }

    private List<User> seedCreators(Random random) {
        List<User> creators = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String name = pickKoreanName(random) + " " + CREATOR_TITLES.get(random.nextInt(CREATOR_TITLES.size()));
            creators.add(new User(name, UserRole.CREATOR));
        }
        return userRepository.saveAll(creators);
    }

    private List<User> seedClassmates(Random random) {
        List<User> classmates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            classmates.add(new User(pickKoreanName(random), UserRole.CLASSMATE));
        }
        return userRepository.saveAll(classmates);
    }

    private List<Lecture> seedLectures(List<User> creators, Random random) {
        List<Lecture> lectures = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < LECTURE_TEMPLATES.size(); i++) {
            LectureTemplate t = LECTURE_TEMPLATES.get(i);
            User creator = creators.get(random.nextInt(creators.size()));

            int durationDays = t.weeks() * 7;
            int offsetDays = random.nextInt(40) - 5;
            LocalDate start = today.plusDays(Math.max(offsetDays, 1));
            LocalDate end = start.plusDays(durationDays);

            int capacity = 3 + random.nextInt(18);
            BigDecimal price = BigDecimal.valueOf(t.price());

            Lecture lecture = new Lecture(creator.getId(), t.title(), t.description(), price, capacity, start, end);

            LectureStatus status = pickStatus(i);
            if (status != LectureStatus.DRAFT) {
                lecture.changeStatus(LectureStatus.OPEN);
            }
            if (status == LectureStatus.CLOSED) {
                lecture.changeStatus(LectureStatus.CLOSED);
            }
            lectures.add(lecture);
        }
        return lectureRepository.saveAll(lectures);
    }

    private LectureStatus pickStatus(int index) {
        if (index < 3) return LectureStatus.DRAFT;
        if (index < LECTURE_TEMPLATES.size() - 3) return LectureStatus.OPEN;
        return LectureStatus.CLOSED;
    }

    private String pickKoreanName(Random random) {
        return SURNAMES.get(random.nextInt(SURNAMES.size())) + GIVEN_NAMES.get(random.nextInt(GIVEN_NAMES.size()));
    }

    private long countByStatus(List<Lecture> lectures, LectureStatus status) {
        return lectures.stream().filter(l -> l.getStatus() == status).count();
    }

    private record LectureTemplate(String title, String description, int price, int weeks) {
    }
}
