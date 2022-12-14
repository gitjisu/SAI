package com.ssafy.sai.domain.member.service;

import com.ssafy.sai.domain.job.domain.Enterprise;
import com.ssafy.sai.domain.job.domain.InterestedEnterprise;
import com.ssafy.sai.domain.job.domain.InterestedJob;
import com.ssafy.sai.domain.job.domain.Job;
import com.ssafy.sai.domain.job.dto.EnterpriseName;
import com.ssafy.sai.domain.job.dto.InterestedEnterpriseCreateRequest;
import com.ssafy.sai.domain.job.dto.InterestedJobCreateRequest;
import com.ssafy.sai.domain.job.dto.JobName;
import com.ssafy.sai.domain.job.repository.EnterpriseRepository;
import com.ssafy.sai.domain.job.repository.InterestedEnterpriseRepository;
import com.ssafy.sai.domain.job.repository.InterestedJobRepository;
import com.ssafy.sai.domain.job.repository.JobRepository;
import com.ssafy.sai.domain.member.domain.Campus;
import com.ssafy.sai.domain.member.domain.Member;
import com.ssafy.sai.domain.member.dto.response.MemberLoginResponse;
import com.ssafy.sai.domain.member.dto.response.ConsultantResponse;
import com.ssafy.sai.domain.member.dto.request.ConsultantSignUpRequest;
import com.ssafy.sai.domain.member.dto.request.MemberLoginRequest;
import com.ssafy.sai.domain.member.dto.request.MemberSignUpRequest;
import com.ssafy.sai.domain.member.dto.response.MemberSimpleResponse;
import com.ssafy.sai.domain.member.exception.MemberException;
import com.ssafy.sai.domain.member.exception.MemberExceptionType;
import com.ssafy.sai.domain.member.repository.CampusRepository;
import com.ssafy.sai.domain.member.repository.MemberRepository;
import com.ssafy.sai.global.util.validation.Empty;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignService {

    private final MemberRepository memberRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    private final ModelMapper modelMapper;
    private final InterestedEnterpriseRepository interestedEnterpriseRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final JobRepository jobRepository;
    private final InterestedJobRepository interestedJobRepository;
    private final CampusRepository campusRepository;

    /**
     * @????????? ????????? ???????????? ?????????
     * @param request ????????? ???????????? ??? ??????
     * @return ????????? ?????? DTO
     * @throws Exception ?????? ???????????? ???????????? ??????, ???????????? ????????? ?????? ?????? ??????
     */
    @Transactional
    public MemberSimpleResponse signUpMember(MemberSignUpRequest request) throws MemberException {

        // ????????? ????????????
        if (!Empty.validation(memberRepository.countByEmail(request.getEmail()))) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_EMAIL);
        }

        // ????????? ????????????
        if (!Empty.validation(memberRepository.countByPhone(request.getPhone()))) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_PHONE);
        }

        // ???????????? ?????????
        request.setPassword(passwordEncoder.encode(request.getPassword()));

        // Member -> DTO
        Member memberDto = Member.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(request.getPassword())
                .name(request.getName())
                .birthday(request.getBirthday())
                .memberStatus(request.getMemberStatus())
                .year(request.getYear())
                .phone(request.getPhone())
                .build();

        Member findMember = memberRepository.save(memberDto);

        // ???????????? ??????
        for (EnterpriseName enterpriseName : request.getInterestedEnterprises()) {
            Enterprise enterprise = enterpriseRepository.findEnterpriseByName(enterpriseName.getName());
            InterestedEnterpriseCreateRequest interestedEnterpriseCreateRequest = new InterestedEnterpriseCreateRequest(enterprise, memberDto);
            InterestedEnterprise interestedEnterprise = interestedEnterpriseCreateRequest.toEntity();
            interestedEnterpriseRepository.save(interestedEnterprise);
        }

        // ???????????? ??????
        for (JobName jobName : request.getInterestedJobs()) {
            Job job = jobRepository.findJobByName(jobName.getName());
            InterestedJobCreateRequest interestedJobCreateRequest = new InterestedJobCreateRequest(job, findMember);
            InterestedJob interestedJob = interestedJobCreateRequest.toEntity();
            interestedJobRepository.save(interestedJob);
        }

        // ????????? ?????? ??????
        Campus campus = campusRepository.findByCityAndClassNumber(request.getCampus().getCity(), request.getCampus().getClassNumber())
                .orElseThrow(() -> new MemberException(MemberExceptionType.WRONG_MEMBER_INFORMATION));
        findMember.updateCampus(campus);

        return new MemberSimpleResponse(findMember);
    }

    /**
     * @????????? ???????????? ???????????? ?????????
     * @param request ???????????? ???????????? ??? ??????
     * @return ???????????? ?????? DTO
     * @throws Exception ?????? ???????????? ???????????? ??????, ???????????? ????????? ?????? ?????? ??????
     */
    @Transactional
    public ConsultantResponse signUpConsultant(ConsultantSignUpRequest request) throws MemberException {

        // ????????? ????????????
        if (!Empty.validation(memberRepository.countByEmail(request.getEmail()))) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_EMAIL);
        }
        // ????????? ????????????
        if (!Empty.validation(memberRepository.countByPhone(request.getPhone()))) {
            throw new MemberException(MemberExceptionType.ALREADY_EXIST_PHONE);
        }
        // ???????????? ?????????
        request.setPassword(passwordEncoder.encode(request.getPassword()));

        // Member -> DTO
        Member memberDto = Member.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .name(request.getName())
                .birthday(request.getBirthday())
                .memberStatus(request.getMemberStatus())
                .phone(request.getPhone())
                .build();

        // ????????? ??????
        Member findMember = memberRepository.save(memberDto);

        // ????????? ?????? ??????
        Campus campus = campusRepository.findByCityAndClassNumber(request.getCampus().getCity(), null)
                .orElseThrow(() -> new MemberException(MemberExceptionType.WRONG_MEMBER_INFORMATION));
        findMember.updateCampus(campus);

        return new ConsultantResponse(findMember);
    }

    /**
     * @????????? ?????? ????????? ?????????
     * @param request ????????? ??? ??????(?????????, ????????????)
     * @return ????????? ?????? DTO
     * @throws Exception ???????????? ???????????? ?????? ??????, ??????????????? ???????????? ?????? ?????? ?????? ??????
     */
    @Transactional
    public MemberLoginResponse loginMember(MemberLoginRequest request) throws MemberException {

        // Member -> DTO
        Member memberDto = Member.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .build();

        // Email ???????????? ??????
        Member findMember = memberRepository.findByEmail(memberDto.getEmail())
                .orElseThrow(() -> new MemberException(MemberExceptionType.NOT_FOUND_MEMBER));

        // ???????????? ?????? ??????
        if (!passwordEncoder.matches(memberDto.getPassword(), findMember.getPassword())) {
            throw new MemberException(MemberExceptionType.WRONG_PASSWORD);
        }

        return modelMapper.map(findMember, MemberLoginResponse.class);
    }

}