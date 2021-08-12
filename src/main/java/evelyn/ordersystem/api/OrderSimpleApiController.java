package evelyn.ordersystem.api;

import evelyn.ordersystem.domain.Order;
import evelyn.ordersystem.repository.OrderRepository;
import evelyn.ordersystem.repository.order.simplequery.OrderSimpleQueryDto;
import evelyn.ordersystem.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderSimpleQueryRepository orderSimpleQueryRepository;
    private final OrderRepository orderRepository;

    @GetMapping("/proxy/order")
    public void findOrder(){
        Order order = orderRepository.findOne(4L);
        System.out.println("order.getClass() = " + order.getClass());
        System.out.println("order.getMember().getClass() = " + order.getMember().getClass());
    }

    @GetMapping("/proxy/orders")
    public void findOrders(){
        List<Order> orders = orderRepository.findAll();
        for(Order order : orders){
            order.getMember().getName();
            order.getDelivery().getAddress();
        }

        Order order = orders.get(0);
        System.out.println("order.getClass() = " + order.getClass());
        System.out.println("order.getMember().getClass() = " + order.getMember().getClass());
    }

    @GetMapping("/api/simple-orders")
    public List<OrderSimpleQueryDto> orders() {
        return orderSimpleQueryRepository.findOrderDtos();
    }
}
