package evelyn.ordersystem.api;

import evelyn.ordersystem.domain.Order;
import evelyn.ordersystem.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;

    @GetMapping("/api/orders")
    public List<Order> orders(){
        List<Order> all = orderRepository.findAll();
        for(Order order : all){
            order.getMember().getName();
            order.getDelivery().getAddress();
        }
        return all;
    }
}
