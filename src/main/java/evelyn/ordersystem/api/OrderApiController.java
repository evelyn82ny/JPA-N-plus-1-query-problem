package evelyn.ordersystem.api;

import evelyn.ordersystem.domain.Order;
import evelyn.ordersystem.domain.OrderItem;
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
    public List<Order> order(){
        List<Order> orders = orderRepository.findAll();
        for(Order order : orders){
            order.getMember().getName();
            order.getDelivery().getAddress();

            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return orders;
    }
}
