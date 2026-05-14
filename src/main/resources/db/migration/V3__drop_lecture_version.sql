-- Lecture.@Version 제거에 따른 컬럼 정리.
-- Lecture row 의 정합성은 비관 락(SELECT ... FOR UPDATE) 으로 처리한다.
-- enrolled_count 갱신은 EnrollmentService.apply/cancel, status 변경은 LectureService.changeStatus 가
-- 모두 LectureRepository#findByIdForUpdate 를 통해 같은 락 경로로 직렬화되므로 낙관 락이 불필요하다.
ALTER TABLE lectures
    DROP COLUMN version;
