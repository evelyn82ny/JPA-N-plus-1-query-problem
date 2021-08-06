package evelyn.ordersystem.api;

import evelyn.ordersystem.repository.order.simplequery.OrderSimpleQueryDto;
import evelyn.ordersystem.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderSimpleQueryRepository orderSimpleQueryRepository;

    @GetMapping("/api/orders")
    public List<OrderSimpleQueryDto> orders() {
        return orderSimpleQueryRepository.findOrderDtos();
    }
}
