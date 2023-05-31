package com.lastone.core.repository.recruitment;

import com.lastone.core.domain.application.ApplicationStatus;
import com.lastone.core.domain.recruitment.*;
import com.lastone.core.domain.recruitment_img.RecruitmentImg;
import com.lastone.core.dto.recruitment.QRecruitmentDetailDto;
import com.lastone.core.dto.recruitment.RecruitmentDetailDto;
import com.lastone.core.dto.recruitment.RecruitmentListDto;
import com.lastone.core.dto.recruitment.RecruitmentSearchCondition;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.*;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static com.lastone.core.domain.application.QApplication.application;
import static com.lastone.core.domain.gym.QGym.gym;
import static com.lastone.core.domain.member.QMember.member;
import static com.lastone.core.domain.recruitment.QRecruitment.recruitment;
import static com.lastone.core.domain.recruitment_img.QRecruitmentImg.recruitmentImg;

@RequiredArgsConstructor
public class RecruitmentRepositoryImpl implements RecruitmentRepositoryCustom{
    private final JPAQueryFactory queryFactory;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_SIZE = 6;
    private static final int DEFAULT_MAIN_PAGE_SIZE = 9;

    @Override
    public RecruitmentDetailDto getDetailDto(Long recruitmentId) {
        RecruitmentDetailDto recruitmentDetailDto = queryFactory
                .select(new QRecruitmentDetailDto(
                        recruitment.member.id,
                        recruitment.member.nickname,
                        recruitment.member.profileUrl,
                        recruitment.member.workoutPurpose,
                        recruitment.gym,
                        recruitment.id,
                        recruitment.title,
                        recruitment.description,
                        recruitment.preferGender,
                        recruitment.createdAt,
                        recruitment.startedAt))
                .from(recruitment)
                .where(
                        recruitment.id.eq(recruitmentId),
                        recruitment.isDeleted.eq(false)
                )
                .fetchOne();

        if (ObjectUtils.isEmpty(recruitmentDetailDto)) {
            return null;
        }

        List<String> imgUrls = queryFactory.select(recruitmentImg.imgUrl)
                .from(recruitmentImg)
                .where(recruitmentImg.recruitment.id.eq(recruitmentId))
                .fetch();

        recruitmentDetailDto.setImgUrls(imgUrls);
        return recruitmentDetailDto;
    }

    @Override
    public List<RecruitmentListDto> getListDtoInMainPage() {
        List<Recruitment> content = queryFactory
                .selectFrom(recruitment)
                .leftJoin(recruitment.member, member).fetchJoin()
                .leftJoin(recruitment.gym, gym).fetchJoin()
                .leftJoin(recruitment.recruitmentImgs, recruitmentImg).fetchJoin()
                .where(
                        isRecruitingOrNot(true),
                        recruitment.isDeleted.eq(false)
                )
                .orderBy(recruitment.createdAt.desc())
                .limit(DEFAULT_MAIN_PAGE_SIZE)
                .fetch();
        return RecruitmentListDto.toDto(content, true);
    }

    @Override
    public Slice<RecruitmentListDto> getListDto(RecruitmentSearchCondition searchCondition) {

        Pageable pageable = PageRequest.of(DEFAULT_OFFSET, DEFAULT_SIZE);

        List<Recruitment> content = queryFactory
                .selectFrom(recruitment)
                .leftJoin(recruitment.member, member).fetchJoin()
                .leftJoin(recruitment.gym, gym).fetchJoin()
                .where(
                        eqWorkoutPart(searchCondition.getWorkoutPart()),
                        eqPreferGender(searchCondition.getPreferGender()),
                        isRecruitingOrNot(searchCondition.getIsRecruiting()),
                        eqStartedAt(searchCondition.getLocalDateTime()),
                        eqGymName(searchCondition.getGymName()),
                        isMatchingTitleOrGymName(searchCondition.getTitle()),
                        ltLastId(searchCondition.getLastId()),
                        recruitment.isDeleted.eq(false)
                )
                .orderBy(recruitment.createdAt.desc())
                .limit(DEFAULT_SIZE + 1)
                .fetch();

        return checkLastPage(pageable, content);
    }

    @Override
    public Recruitment findOneCompleteStatusAtTodayByMemberId(Long memberId) {

        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime tomorrow = today.plusDays(1);

        return queryFactory
                .selectFrom(recruitment)
                .where(
                        recruitment.member.id.eq(memberId),
                        recruitment.recruitmentStatus.eq(RecruitmentStatus.COMPLETE),
                        recruitment.startedAt.between(today, tomorrow))
                .leftJoin(recruitment.applications, application).fetchJoin()
                .leftJoin(recruitment.gym, gym).fetchJoin()
                .fetchFirst();
    }

    @Override
    public List<Recruitment> findAllCompleteStatusBeforeToday(Long memberId) {

        LocalDateTime now = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        return queryFactory
                .selectFrom(recruitment)
                .where(
                        recruitment.member.id.eq(memberId),
                        recruitment.recruitmentStatus.eq(RecruitmentStatus.COMPLETE),
                        recruitment.startedAt.loe(now))
                .leftJoin(recruitment.applications, application).fetchJoin()
                .orderBy(recruitment.startedAt.asc())
                .fetch();
    }

    private BooleanExpression eqWorkoutPart(WorkoutPart workoutPart) {
        if (workoutPart == null) {
            return null;
        }
        return recruitment.workoutPart.eq(workoutPart);
    }

    private BooleanExpression eqPreferGender(PreferGender preferGender) {
        if (preferGender == null || preferGender.equals(PreferGender.BOTH)) {
            return null;
        }
        return recruitment.preferGender.eq(preferGender);
    }

    private BooleanExpression isRecruitingOrNot(Boolean isRecruiting) {
        if (isRecruiting == null || !isRecruiting) {
            return null;
        }
        return recruitment.recruitmentStatus.eq(RecruitmentStatus.RECRUITING)
                .and(recruitment.startedAt.goe(LocalDateTime.now()));
    }

    private BooleanExpression eqStartedAt(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return recruitment.startedAt.year().eq(time.getYear())
                .and(recruitment.startedAt.month().eq(time.getMonthValue()))
                .and(recruitment.startedAt.dayOfMonth().eq(time.getDayOfMonth()));
    }

    private BooleanExpression isMatchingTitleOrGymName(String searchText) {
        if (!StringUtils.hasText(searchText)) {
            return null;
        }
        return recruitment.title.contains(searchText)
                .or(recruitment.gym.name.contains(searchText));
    }

    private BooleanExpression eqGymName(String gymName) {
        if (ObjectUtils.isEmpty(gymName)) {
            return null;
        }
        return recruitment.gym.name.eq(gymName);
    }

    private BooleanExpression ltLastId(Long lastId) {
        if (lastId == null) {
            return null;
        }
        return recruitment.id.lt(lastId);
    }

    private Slice<RecruitmentListDto> checkLastPage(Pageable pageable, List<Recruitment> recruitments) {
        boolean hasNext = false;
        if (recruitments.size() > DEFAULT_SIZE) {
            hasNext = true;
            recruitments.remove(DEFAULT_SIZE);
        }
        List<RecruitmentListDto> content = RecruitmentListDto.toDto(recruitments, false);
        return new SliceImpl<>(content, pageable, hasNext);
    }
}