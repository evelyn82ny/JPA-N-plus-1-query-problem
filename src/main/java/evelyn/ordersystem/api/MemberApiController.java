package evelyn.ordersystem.api;

import evelyn.ordersystem.domain.Member;
import evelyn.ordersystem.service.MemberService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @PostMapping("/api/members")
    public CreateMemberResponse saveMember (@RequestBody @Validated CreateMemberRequest request){
        Member member = new Member();
        member.setName(request.getName());

        Long join = memberService.join(member);
        return new CreateMemberResponse(join);
    }

    @Data
    static class CreateMemberRequest {
        private String name;
    }

    @Data
    class CreateMemberResponse {
        private Long id;

        public CreateMemberResponse(Long id){
            this.id = id;
        }
    }

    @PutMapping("/api/members/{id}")
    public UpdateMemberResponse updateMember(@PathVariable Long id, @RequestBody @Validated UpdateMemberRequest request){
        memberService.update(id, request.getName());
        Member findMember = memberService.findOne(id);
        return new UpdateMemberResponse(findMember.getId(), findMember.getName());
    }

    @Data
    static class UpdateMemberRequest{
        private String name;
    }

    @Data
    @AllArgsConstructor
    class UpdateMemberResponse{
        private Long id;
        private String name;
    }

    @GetMapping("/api/members")
    public Result members(){
        List<Member> findMembers = memberService.findMembers();
        List<MemberDto> collect = findMembers.stream()
                .map(m -> new MemberDto(m.getName()))
                .collect(Collectors.toList());
        return new Result(collect);
    }

    @Data
    @AllArgsConstructor
    class Result<T>{
        private T data;
    }
    @Data
    @AllArgsConstructor
    class MemberDto{
        private String name;
    }
}
