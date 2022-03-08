> 주문 시스템을 구현하며 JPA N + 1 쿼리 문제를 해결한다.

![png](/_image/order_system_erd.png)

- [Order 와 Member 관계 : @ManyToOne](#ManyToOne)
- [양방향 관계 @JsonIgnore 설정](#bidirectional-relationship----)
- [지연 로딩에 대한 Type definition error 발생](#Type-definition-error)
- [JPA N + 1 쿼리 문제](#JPA-N-plus-1)
- [컬렉션 조회](#collection-)
- [fetch join](#fetch-join-)
- [paging 불가능](#paging)
- [batch fetch size 설정](#hibernatedefault_batch_fetch_size-)
- [where in 절로 N + 1 쿼리 해결](#where-in--n--1--)

## ManyToOne

- member 관련 커밋 [daa3890](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/daa3890a4446bb0aab792751319b6e67eeb107dd)
- order 관련 커밋 [ea0afef](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/ea0afefe6c921bea497faaa50fee8e608b0cede2)

회원은 1개의 이상의 주문을 하는 일대다 관계로 ```Member 를 One``` 으로, ```Order 를 Many``` 로 설정한다.

```java
public class Order { 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}
```
@JoinColumn에 Member entity 의 PK 컬럼명을 작성해 ```@ManyToOne``` 관계에서 **Order 를 주인으로 설정**한다. ```@OneToMany```, ```@ManyToOne``` 관계에서 주인은 PK 로 등록, 수정, 삭제를 할 수 있지만, **주인이 아닌 쪽에서 등록, 수정, 삭제를 시도해도 반영되지 않는다**.

```@OneToMany``` 관계를 join 해서 읽어오면 row 수가 증가해 많은 데이터가 발생되므로 ```fetch = FetchType.LAZY``` 로 설정해 Order 정보를 불러올 때 Member 에 대한 정보를 가져오지 않도록 설정한다.<br>

```java
public class Member{
    @Id @Column(name = "member_id")
    private Long id;
    
    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();
}
```
Order 와 Member 관계에서 Member 가 One 이므로 ```@OneToMany``` 를 작성했지만, 사실 ```@OneToMany```, ```@ManyToOne``` 관계에서 **One 인 클래스는 해당 필드를 작성하지 않아도 된다.** ```@ManyToOne``` 의 Many 인 주인 entity 에서 역으로 조회가능하므로 작성할 필요없다.

Order 와 Member 관계에서 Member 는 주인이 아니라는 것을 명시하기 위해 ```mappedBy = "PK가 있는 필드명"``` 를 작성한다. ```mappedBy = "member"``` 설정으로 ```@OneToMany``` 관계에서 One 인 Member 엔티티는 수정 권한이 없어 update를 시도해도 변경이 일어나지 않는다.
<br>

## @Repository 에서 조회하기 위한 JPQL

- 해당 커밋 [786560a](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/786560a080d44aa08cc1651abcb66ad074b6951e)

Spring Data JPA가 제공하는 JPARepository 를 상속받지 않고 JPQL을 사용해보기 위해 @Repository 만 설정한 MemberRepository 에서 Member 를 조회하기 위한 JPQL를 추가한다.

```java
public List<Member> findAll(){
    return em.createQuery("select m from Member m", Member.class)
            .getResultList();
}
```
모든 Member entity 를 List 에 담아 반환한다.


```java
public List<Member> findByName(String name){
    return em.createQuery(
    	"select m from Member m where m.name = :name", Member.class)
            .setParameter("name", name)
            .getResultList();
    }
```
파라미터로 전달된 name 과 Member entity 의 name 필드가 동일한 entity 를 List 에 담아 반환한다.

```java
@PersistenceContext
private final EntityManager em;
```
Spring Data JPA 를 사용하지 않으면 **EntityManager를 직접 작성해야 entity CRUD 처리가 가능**하다.<br>

- ```@PersistenceContext``` : EntityManger 주입
- ```@PersistenceUnit``` : EntityManagerFactory 주입
<br>

## Bidirectional relationship 에서 발생되는 무한 루트

- 해당 커밋 [c0a7d87](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/c0a7d87e6a99b9b2023d908fd14850230d3e683f)

![png](/_image/order_system_erd2.png)

```java
@GetMapping("/api/orders")
public List<Order> orders(){
    List<Order> all = orderRepository.findAll();
    for(Order order : all){
        order.getMember().getName();
        order.getDelivery().getAddress();
    }
    return all;
  }
```
order repository 에 있는 모든 주문을 가져오는 method 를 작성한다. 해당 API 에 대해 GET 요청하면 다음과 같은 결과를 얻는다.

![png](/_image/to_one_infinite_loop.png)

postman 에서 Get 요청을 보내면 **무한루프가 발생**된다.

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}
```
```java
@Entity
public class Member {
    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();
}
```
order 에서 member 를 참조하고, member 에 order 를 참조하는 양방향 관계이다. 그렇기 때문에 order -> member -> order ... 순으로 계속 탐색되어 무한루프가 발생된 것이다.

즉, 양방향 관계에선 한쪽에 ```@JsonIgnore``` 를 추가해 무한루프 발생을 막는다.

- 해당 커밋 [0d1807d](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/0d1807db7fcf45d7ff1ea45c881677fc981be1c7)

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}
```
```java
@Entity
public class Member {
    @JsonIgnore
    @OneToMany(mappedBy = "member")
    private List<Order> orders = new ArrayList<>();
}
```

Order 와 Member 관계에서 주인이 아닌 Member Entity 에 ```@JsonIgnore``` 를 추가하면 조회 시 해당 필드는 ignore 되어 무한루프를 막는다.
<br>

## Type definition error

양방향 관계로 인해 발생되는 무한루프를 해결했지만 지연 로딩에 대한 에러가 발생한다.

```java
@Entity
public class Order { 
    
    @Id @GeneratedValue
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}
```
```Order entity``` 에서 ```Member entity``` 를 지연 로딩으로 설정했기 때문에 ```Order entity``` 를 호출해도 ```Member``` 에 대한 정보를 가져오지 않으니 DB에 접근하지 않는다. Member 객체에 직접 접근하는 시점에 쿼리가 발생해 DB 에 접근한다.

```http://localhost:8080/api/orders``` Get 요청 시 500 상태 코드와 함께 아래 오류 메시지를 출력한다.

```text
Servlet.service() for servlet [dispatcherServlet] in context 
with path [] threw exception [Request processing failed; 
nested exception is org.springframework
.http.converter.HttpMessageConversionException: 

Type definition error: [simple type, class org
.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor]; 
nested exception is com.fasterxml
.jackson.databind.exc.InvalidDefinitionException: 

No serializer found for class org
.hibernate.proxy.pojo.bytebuddy.ByteBuddyInterceptor
more...
```

오류 메시지를 읽어보면 ```ByteBuddyInterceptor``` 에서 ```Type definition error``` 가 발생했다. 지연 로딩으로 설정했기 때문에 Order 를 조회할 때 Member 에 대한 정보는 가져오지 않는다.

지연 로딩 설정에 대해서 **hibernate 는 proxy 객체를 생성**하기 때문에 ```ByteBuddyInterceptor``` 객체로 초기화를 시도한다. proxy 객체로 일단 채워두고 **직접 요청하는 경우에만 쿼리를 날려 프록시 초기화**를 한다.

즉, Member 객체를 조회하는 시점에는 Member 객체가 아닌 프록시 객체를 가지고 있는데 jackson 라이브러리는 프록시 객체를 json으로 어떻게 생성해야 하는지 모르기 때문에 에러가 발생한다. 지연 로딩을 해결하기 위해 hibernate5Module 을 사용하여 에러를 해결할 수 있다.
<br>

### hibernate5Module 적용

- 해당 커밋 [89341e9](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/89341e9fbc6ea08f6a2f8549165a066dead7f116)
- hibernate5Module 의존성 추가 : https://mvnrepository.com/artifact/com.fasterxml.jackson.datatype/jackson-datatype-hibernate5

지연로딩으로 설정된 프록시 엔티티를 null 값으로 설정하기 위해 ```hibernate5Module``` 를 사용한다.

```java
@GetMapping("/api/orders")
public List<Order> orders(){
    List<Order> all = orderRepository.findAll();
    for(Order order : all){
        order.getMember();
        order.getDelivery();
    }
    return all;
}
```
![png](/_image/apply_hibernate5.png)

```hibernate5Module``` 을 적용하면 위에서 발생했던 bytebuddy.ByteBuddyInterceptor 관련 에러가 발생하지 않고 **지연 로딩으로 설정된 필드는 null 로 출력**된다.
<br>

### 강제 초기화

주문에서 주문한 멤버를 불러오는 ```order.getMember()``` 호출 시 실제 DB에 접근하지 않고 null을 반환한다. 하지만 주문한 멤버의 이름까지 요구하는 ```order.getMember().getName()``` 호출 시 **LAZY 설정이 강제 초기화 되어 DB에 접근**한다.

```java
@GetMapping("/api/orders")
public List<Order> orders(){
    List<Order> all = orderRepository.findAll();
    for(Order order : all){
        order.getMember().getName(); // 강제 초기화
        order.getDelivery().getAddress();
    }
    return all;
}
```

![png](/_image/apply_hibernate5_lazy.png)

LAZY 설정한 필드를 강제 초기화하면 null값이 아닌 해당 객체까지 잘 출력되는 것을 알 수 있다.

하지만 **Entity 자체를 파라미터로 받거나 API 응답으로 외부에 노출시키는 것은 좋은 방법이 아니기 때문에** DTO를 적용한다.

- DTO 적용 커밋 [6beb35c](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/6beb35cf8b14c7a9421ba940eb7108bece8200a5)

```java
@GetMapping("/api/orders")
public List<SimpleOrderDto> orders(){
    List<Order> orders = orderRepository.findAll();
    List<SimpleOrderDto> result = orders.stream()
                    .map(o -> new SimpleOrderDto(o))
                    .collect(toList());

    return result;
}

@Data
static class SimpleOrderDto{
	private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;
}
```
<br>

## JPA N plus 1

![png](/_image/init_orders_table.png)

현재 다음과 같이 2개의 주문이 있다. 각 주문의 MEMBER_ID를 보면 1과 8로 서로 다른 멤버가 주문한 것을 알 수 있다.

모든 주문 데이터를 가져오기 위해 ```http://localhost:8080/api/orders``` 를 요청 시 발생되는 쿼리는 다음과 같다.

```text
// 1. orderRepository에서 모든 주문 orders를 가져오기 위한 쿼리 발생

 select
        order0_.order_id as order_id1_6_,
        order0_.delivery_id as delivery4_6_,
        order0_.member_id as member_i5_6_,
        order0_.order_date as order_da2_6_,
        order0_.status as status3_6_ 
    from
        orders order0_
------------------------------------------------
// 2. order_id 4 를 주문한 member_id 가 1 인 멤버의
        getName()을 수행하기 위한 쿼리 발생
        
 select
        member0_.member_id as member_i1_4_0_,
        member0_.city as city2_4_0_,
        member0_.street as street3_4_0_,
        member0_.zipcode as zipcode4_4_0_,
        member0_.name as name5_4_0_ 
    from
        member member0_ 
    where
        member0_.member_id=?
        orders order0_
------------------------------------------------
// 3. order_id 4 주문의 getAddress()를 수행하기 위한 쿼리 발생

 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
        orders order0_
------------------------------------------------
// 4. order_id 11 를 주문한 member_id 가 8 인 멤버의
        getName()을 수행하기 위한 쿼리 발생
        
 select
        member0_.member_id as member_i1_4_0_,
        member0_.city as city2_4_0_,
        member0_.street as street3_4_0_,
        member0_.zipcode as zipcode4_4_0_,
        member0_.name as name5_4_0_ 
    from
        member member0_ 
    where
        member0_.member_id=?
        orders order0_
------------------------------------------------
// 5. order_id 11 주문의 getAddress()을 수행하기 위한 쿼리 발생

 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
```

총 5개의 쿼리가 발생한다.

1. OrderRepository 에서 **모든 주문 orders** 를 가져오기 위한 쿼리 발생
2. order_id 4 를 주문한 ```member_id 1 인 멤버``` 의 getName()을 수행하기 위한 쿼리 발생
3. order_id 4 주문의 getAddress() 을 수행하기 위한 쿼리 발생
4. order_id 11 주문한 ```member_id 8 인 멤버``` 의 getName()을 수행하기 위한 쿼리 발생
5. order_id 11 주문의 getAddress() 을 수행하기 위한 쿼리 발생

5개의 쿼리를 정확하게 보면 ```모든 주문을 위한 1개``` + ```order Id 가 4 인 주문에서 발생하는 2개의 쿼리``` + ```order Id 가 11 인 주문에서 발생하는 2개의 쿼리``` 로 나눌 수 있다. 물론 이 경우는 모든 쿼리가 발생하는 최악의 경우를 말한다.

이렇게 모든 경우에 쿼리가 발생하는 최악의 경우를 **N + 1 문제**라고 한다.

영속성 컨텍스트는 1차 캐시에 이미 존재하는 객체를 또 다시 조회할 경우 쿼리가 발생되지 않는다는 점을 이용해 최악의 경우가 아닌 상황에서 쿼리 발생이 적어지는지 확인해 보았다. 실제로 적용되는지 보기 위해 아래와 같이 주문 테이블에서 MEMBER_ID 를 같은 멤버로 수정한 뒤 실행했다.

![png](/_image/order_table_modify_member_id.png)

```text
// 1. orderRepository에서 모든 주문 orders를 가져오기 위한 쿼리 발생

 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
------------------------------------------------
// 2. order_id 4 주문자인 member_id 가 1 인 멤버의
        getName()을 수행하기 위한 쿼리 발생
        
 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
------------------------------------------------
// 3. order_id 4 주문의 getAddress()을 수행하기 위한 쿼리 발생

 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
------------------------------------------------
// 4. order_id 11 주문의 getAddress()을 수행하기 위한 쿼리 발생

 select
        delivery0_.delivery_id as delivery1_2_0_,
        delivery0_.city as city2_2_0_,
        delivery0_.street as street3_2_0_,
        delivery0_.zipcode as zipcode4_2_0_,
        delivery0_.status as status5_2_0_ 
    from
        delivery delivery0_ 
    where
        delivery0_.delivery_id=?
```

예상했던 것과 같이 ```모든 주문을 위한 1개``` + ```order Id 가 4인 주문에서 발생하는 2개의 쿼리``` + ```order Id 가 11 인 주문에서 발생하는 1개의 쿼리```로 총 4개의 쿼리가 발생한다. order Id 가 11 인 주문의 getName() 호출에서 쿼리가 발생하지 않은 이유는 MEMBER_ID가 1인 객체가 이미 1차 캐시 때문이다.

order Id 가 4 인 주문에서 getName() 호출하며 MEMBER_ID 가 1인 엔티티가 1차 캐시에 저장되었고, order Id 가 11 인 주문에서 getName()을 호출했을 때 이미 1차 캐시에 MEMBER_ID 1 인 엔티티가 존재하기 때문에 쿼리가 발생하지 않는다.

이런식으로 쿼리를 줄일 수 있지만 최악의 경우 대비해야 되기 때문에 최악의 경우에 중점을 두고 작성해야하며, 1차 캐시를 이용해 쿼리를 줄인다는 것은 그닥 많은 효과가 있을 것 같지 않다.
<br>

## Collection 조회

```@OneToMany``` 관계를 조회하는 것을 컬렉션 조회라고 한다. 1개의 주문에 여러 주문 상품이 있는 경우가 일대다 관계인데 fetch join 할 경우 엄청나게 많은 데이터를 읽어온다.<br>

- 해당 커밋 [b55c69a](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/b55c69a1f19f8ecd693d653587dd69d954ef56b5)

```java

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
```
![png](/_image/collection_lookup.png)

지연 로딩으로 설정한 orderItem 을 강제 초기화하면 주문한 아이템의 상세까지 다 출력되는 것을 알 수 있다.

모든 주문 orders 를 가져오는 1개 쿼리가 발생하고, **1 개의 주문에서 3 개 쿼리가 발생**한다.<br>

- member 의 이름을 조회하는 쿼리: getName()
- 배달 주소를 조회하는 쿼리: getAddress()
- 주문한 아이템을 모두 가져오는 쿼리: .getItem().getName()

1 개의 주문에서 3 개의 쿼리가 발생하고, 주문한 아이템에 대한 각각의 데이터를 가져오기 위해 **주문한 아이템 수 n 개의 쿼리가 추가로 발생**한다. 이렇게 OneToMany 관계에서는 엄청나게 많은 쿼리가 발생한다.
<br>

일단 entity 자체를 반환하는 것을 막고 원하는 데이터만 출력하도록 DTO를 적용한다.

- 해당 커밋 [f74cbc8](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/f74cbc8e437257d8224fc10cd681ee11d368a7c4)

```java
@GetMapping("/api/orders")
public List<OrderDto> order() {
	List<Order> orders = orderRepository.findAll();
    List<OrderDto> result = orders.stream()
                        .map(o -> new OrderDto(o))
                        .collect(Collectors.toList());
    return result;
}
```

![png](/_image/collection_lookup_apply_dto.png)

DTO를 적용으로 원하는 데이터만 출력되었으며 발생되는 쿼리는 위와 동일하게 많다.
<br>

## DTO 로 반환하는 API

- 해당 커밋 [ddbb74e](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/ddbb74ecf779a474f31d054759439a22f662973a)

불필요한 데이터까지 가져오는 것보다 원하는 데이터만 가져오는 최적화를 시도한다.

```java
@Data
public class OrderSimpleQueryDto { 
    private Long orderId;
    private String name;
    private LocalDateTime orderDate;
    private OrderStatus orderStatus;
    private Address address;
    // 생성자
}
```
5 개의 필드만 가져오는 DTO 를 생성했다.

```java
public List<OrderSimpleQueryDto> findOrderDtos() {
    return em.createQuery(
            "select new evelyn.ordersystem.repository.order.simplequery
            .OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
            " from Order o" +
            " join o.member m" +
            " join o.delivery d", OrderSimpleQueryDto.class)
            .getResultList();
}
```
모든 연관 관계를 join 하되 JPQL 결과를 **DTO 로 즉시 변환**한다. SELECT 절에서 원하는 데이터를 직접 선택하기 때문에 애플리케이션 네트워크 용량을 최적화 할 수 있다.

```text
    select
        order0_.order_id as col_0_0_,
        member1_.name as col_1_0_,
        order0_.order_date as col_2_0_,
        order0_.status as col_3_0_,
        delivery2_.city as col_4_0_,
        delivery2_.street as col_4_1_,
        delivery2_.zipcode as col_4_2_ 
    from
        orders order0_ 

    inner join
        member member1_ 
            on order0_.member_id=member1_.member_id 
    
    inner join
        delivery delivery2_ 
            on order0_.delivery_id=delivery2_.delivery_id
```

원하는 데이터만 가지고 오는 JPQL 를 작성하면 모든 데이터를 가지고 오는 경우보다 필요한 데이터만 가지고 오니 상대적으로 최적화할 수 있다.

### 문제점

하지만 여기서 **활용성**이라는 또 다른 문제가 발생한다. 모든 데이터를 가지고 오는 경우 상황에 맞는 DTO 에 적용하면 되므로 활용성이 높다. 하지만 원하는 데이터만 가져오도록 JPQL을 작성하면 활용성이 떨어진다.

repository 의 재사용성을 높이기 위해 DTO 로 변환하지 말고, 순수한 엔티티를 조회하는 용도로 사용하는 게 좋을 것이라 생각한다.

뿐만 아니라 성능은 조회가 아닌 **JOIN 할 때 결정**되는데 두 방식은 같은 JOIN 방식이므로 성능 차이가 크게 나지 않는다.
<br>

## Fetch join 적용

- 해당 커밋 [75be719](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/75be71982518f1903a9d865ad3985ca10cccba46)

모든 연관관계를 join 하고 DTO로 즉시 반환하는 것은 활용성이 떨어지기 때문에 일단 fetch join 만 적용한다.

```java
public List<Order> findAllWithItem() {
    return em.createQuery(
            "select o from Order o" +
                    " join fetch o.member m" +
                    " join fetch o.delivery d" +
                    " join fetch o.orderItems oi" +
                    " join fetch oi.item i", Order.class)
            .getResultList();
}
```

![png](/_image/fetch_join_table_result.png)

예상대로 쿼리는 1 번이 발생하지만 실제 DB 에서 결과를 보면 동일한 order_id, member_id 가 2번씩 출력된다.

fetch join 한 결과 2개의 주문이지만 4개의 결과로 출력되는 이유는 order 가 기준이 아니고 **order_item 을 기준으로 처리**되었기 때문이다.

collection fetch join 한 결과 발생하는 1개의 쿼리는 다음과 같다.
```text
     select
         distinct order0_.order_id as order_id1_6_0_,
         member1_.member_id as member_i1_4_1_,
         delivery2_.delivery_id as delivery1_2_2_,
         orderitems3_.order_item_id as order_it1_5_3_,
         item4_.item_id as item_id2_3_4_,
         order0_.delivery_id as delivery4_6_0_,
         order0_.member_id as member_i5_6_0_,
         order0_.order_date as order_da2_6_0_,
         order0_.status as status3_6_0_,
         member1_.city as city2_4_1_,
         member1_.street as street3_4_1_,
         member1_.zipcode as zipcode4_4_1_,
         member1_.name as name5_4_1_,
         delivery2_.city as city2_2_2_,
         delivery2_.street as street3_2_2_,
         delivery2_.zipcode as zipcode4_2_2_,
         delivery2_.status as status5_2_2_,
         orderitems3_.count as count2_5_3_,
         orderitems3_.item_id as item_id4_5_3_,
         orderitems3_.order_id as order_id5_5_3_,
         orderitems3_.order_price as order_pr3_5_3_,
         orderitems3_.order_id as order_id5_5_0__,
         orderitems3_.order_item_id as order_it1_5_0__,
         item4_.name as name3_3_4_,
         item4_.price as price4_3_4_,
         item4_.stock_quantity as stock_qu5_3_4_,
         item4_.artist as artist6_3_4_,
         item4_.etc as etc7_3_4_,
         item4_.author as author8_3_4_,
         item4_.isbn as isbn9_3_4_,
         item4_.actor as actor10_3_4_,
         item4_.director as directo11_3_4_,
         item4_.dtype as dtype1_3_4_ 
     from
         orders order0_ 
     inner join
         member member1_ 
             on order0_.member_id=member1_.member_id 
     inner join
         delivery delivery2_ 
             on order0_.delivery_id=delivery2_.delivery_id 
     inner join
         order_item orderitems3_ 
             on order0_.order_id=orderitems3_.order_id 
     inner join
         item item4_ 
             on orderitems3_.item_id=item4_.item_id
```

collection fetch join 한 경우 1개의 쿼리가 발생하지만 **일대다 조인으로 인해 데이터가 엄청나게 증가하는 문제** 를 갖고 있다.

fetch join 한 order의 reference 와 주문한 Member 의 Id 를 출력해봤다.

- 해당 커밋 [d77520f](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/d77520f93aa0db2b07a288fc51f5c6998c8bf278)

![png](/_image/fetch_join_result.png)

fetch join 한 order의 reference 를 출력하면 같은 주문이므로 같은 reference가 출력된다.

중복을 제거하기 위해 JPQL 의 distinct를 적용해 중복 처리를 시도했다.
<br>

### JPA distinct

JPQL 의 distinct 는 SQL 에 distinct 를 추가하고 애플리케이션에서 한번 더 중복을 제거한다.<br>

```java
public List<Order> findAllWithItem() {
    return em.createQuery(
            "select distinct o from Order o" +
                    " join fetch o.member m" +
                    " join fetch o.delivery d" +
                    " join fetch o.orderItems oi" + 
                    " join fetch oi.item i", Order.class)
            .getResultList();
}
```

![png](/_image/distinct_apply_only_jpa.png)

distinct를 추가하면 JPA 에서는 id 가 같은 경우 중복을 제거하기 때문에 2개의 주문이 출력된다.

![png](/_image/distinct_not_apply_db.png)

하지만 실제 DB 에서 조회하면 중복이 제거되지 않는다. 실제 DB 에서 중복을 제거하려면 모든 필드의 값이 같아야 하는데 그렇지 않기 때문에 중복처리가 되지 않는다.

collection fetch join 은 많은 데이터가 처리되므로 2개 이상 join 시 데이터가 부정합하게 조회될 수 있어 1개만 사용하는게 좋다.
<br>

## paging

collection fetch join 사용 시 **paging 이 불가능**한 문제점도 발생한다.

order 에 대해 fetch join 한 결과를 두번째 데이터부터 보기 위해 아래와 같은 paging을 설정한다.

```java
public List<Order> findAllWithItem() {
	return em.createQuery(
            "select distinct o from Order o" +
            " join fetch o.member m" +
            " join fetch o.delivery d" +
            " join fetch o.orderItems oi" +
            " join fetch oi.item i", Order.class)
         .setFirstResult(1)  // 0 시작
         .setMaxResults(100)
         .getResultList();
}
```

![png](/_image/collection_fetch_join_result.png)

Order 와 OrderItem 에서 many 인 orderItem 까지 fetch join 한 뒤 두번째 데이터부터 출력되도록 paging 하면 postman 에 제대로 출력되었고 다음과 같이 1개의 쿼리가 발생했다.

 ```text
 	select
         distinct order0_.order_id as order_id1_6_0_,
         member1_.member_id as member_i1_4_1_,
         delivery2_.delivery_id as delivery1_2_2_,
         orderitems3_.order_item_id as order_it1_5_3_,
         item4_.item_id as item_id2_3_4_,
         order0_.delivery_id as delivery4_6_0_,
         order0_.member_id as member_i5_6_0_,
         order0_.order_date as order_da2_6_0_,
         order0_.status as status3_6_0_,
         member1_.city as city2_4_1_,
         member1_.street as street3_4_1_,
         member1_.zipcode as zipcode4_4_1_,
         member1_.name as name5_4_1_,
         delivery2_.city as city2_2_2_,
         delivery2_.street as street3_2_2_,
         delivery2_.zipcode as zipcode4_2_2_,
         delivery2_.status as status5_2_2_,
         orderitems3_.count as count2_5_3_,
         orderitems3_.item_id as item_id4_5_3_,
         orderitems3_.order_id as order_id5_5_3_,
         orderitems3_.order_price as order_pr3_5_3_,
         orderitems3_.order_id as order_id5_5_0__,
         orderitems3_.order_item_id as order_it1_5_0__,
         item4_.name as name3_3_4_,
         item4_.price as price4_3_4_,
         item4_.stock_quantity as stock_qu5_3_4_,
         item4_.artist as artist6_3_4_,
         item4_.etc as etc7_3_4_,
         item4_.author as author8_3_4_,
         item4_.isbn as isbn9_3_4_,
         item4_.actor as actor10_3_4_,
         item4_.director as directo11_3_4_,
         item4_.dtype as dtype1_3_4_ 
     from
         orders order0_ 
     inner join
         member member1_ 
             on order0_.member_id=member1_.member_id 
     inner join
         delivery delivery2_ 
             on order0_.delivery_id=delivery2_.delivery_id 
     inner join
         order_item orderitems3_ 
             on order0_.order_id=orderitems3_.order_id 
     inner join
         item item4_ 
             on orderitems3_.item_id=item4_.item_id 
 ```

Order 와 OrderItem 에서 many 인 orderItem 까지 fetch join 했기 때문에 1개의 쿼리가 발생했다. 하지만 쿼리를 잘 보면 paging 에 대한 **offset** 이나 **limit** 에 대한 정보가 추가되지 않았다.<br>

![png](/_image/collection_fetch_join_db_result.png)

실제 DB를 보면 paging 처리가 전혀되지 않았다. 또한 postman 에는 원하는 데이터가 제대로 출력되었지만 아래의 경고 로그를 남긴다.

```text
WARN 1916 --- [nio-8080-exec-1] o.h.h.internal
.ast.QueryTranslatorImpl    
HHH000104: firstResult/maxResults specified with collection fetch; 
applying in memory!
 ```
fetch 한 모든 데이터를 읽어와 메모리에서 페이징하니 위험하다는 경고이다. 결론은 collection fetch join 사용 시 paging 이 불가능하다.<br>

## hibernate.default_batch_fetch_size 설정

- 해당 커밋 [546edc6](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/546edc62664ba4adbbfb30f7e47230d9a22b340b)

collection fetch join 사용으로 발생되는 ```N + 1 문제``` 와 ```paging 불가능``` 이라는 2가지 문제를 해결하기 위해 **fetch size** 를 설정한다.

xToOne(OneToOne, ManyToOne) 관계는 fetch join 해도 row 수가 증가하지 않으므로 데이터 전송이 급격하게 증가하지 않고 페이징 쿼리에도 영향을 주지 않는다.

반대로 ToMany 를 fetch join하면 데이터 전송이 엄청나게 증가할 뿐만 아니라 paging 도 제대로 처리되지 않는다.

이러한 문제를 해결하기 위해 ToMany 인 collection 은 지연 로딩으로 조회하고 지연 로딩 성능 최적화를 위해 ```hibernate.default_batch_fetch_size``` 나 ```@BatchSize``` 를 적용한다.

- default_batch_fetch_size : 글로벌 설정 (모든 부분에서 동일하게 처리)
- @BatchSize : 필드 개별 최적화


 ```java
 public List<OrderDto> order(@RequestParam(value = "offset", defaultValue = "0") int offset,
                             @RequestParam(value = "limit", defaultValue = "100") int limit) {
                             // offset : 몇번째부터 읽을 것인지, limit : 한번에 읽어올 개수
     List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
     List<OrderDto> result = orders.stream()
              .map(o -> new OrderDto(o))
              .collect(Collectors.toList());
     return result;
 }
 ```

 ```java
 public List<Order> findAllWithMemberDelivery(int offset, int limit) {
     return em.createQuery("select o from Order o" +
                         " join fetch o.member m" +
                         " join fetch o.delivery d", Order.class)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
   }
 ```

위와 같이 작성한 후 application.yml 에 ```default_batch_fetch_size: 100``` 으로 설정하면 총 3개의 쿼리가 발생한다.

1. 모든 주문 orders 를 가져오는 쿼리
2. Order 에서 OneToMany 관계인 ```OrderItem(many)``` 에 대한 쿼리 발생
3. OrderItem 에서 ManyToOne 관계인 ```Item(one)``` 에 대한 쿼리 발생


### 1. 모든 주문 orders를 가져오는 쿼리

```text
  select
        order0_.order_id as order_id1_6_0_,
        member1_.member_id as member_i1_4_1_,
        delivery2_.delivery_id as delivery1_2_2_,
        order0_.delivery_id as delivery4_6_0_,
        order0_.member_id as member_i5_6_0_,
        order0_.order_date as order_da2_6_0_,
        order0_.status as status3_6_0_,
        member1_.city as city2_4_1_,
        member1_.street as street3_4_1_,
        member1_.zipcode as zipcode4_4_1_,
        member1_.name as name5_4_1_,
        delivery2_.city as city2_2_2_,
        delivery2_.street as street3_2_2_,
        delivery2_.zipcode as zipcode4_2_2_,
        delivery2_.status as status5_2_2_ 
  from
        orders order0_ 
  inner join
        member member1_ 
            on order0_.member_id=member1_.member_id 
  inner join
        delivery delivery2_ 
           on order0_.delivery_id=delivery2_.delivery_id limit ?
```

Order 와 ManyToOne 관계인 ```Member```, OneToOne 관계인 ```Delivery``` 를 fetch join 으로 읽어오는 1개의 쿼리가 발생한다.

쿼리 맨 밑을 보면 controller 에 설정한 **limit** 가 제대로 추가된 것을 볼 수 있다. (offset은 0으로 설정했기 때문에 생략된다.)


### 2. Order 에서 OneToMany 관계인 OrderItem(many)에 대한 쿼리 발생

```text
  select
        orderitems0_.order_id as order_id5_5_1_,
        orderitems0_.order_item_id as order_it1_5_1_,
        orderitems0_.order_item_id as order_it1_5_0_,
        orderitems0_.count as count2_5_0_,
        orderitems0_.item_id as item_id4_5_0_,
        orderitems0_.order_id as order_id5_5_0_,
        orderitems0_.order_price as order_pr3_5_0_ 
  from
        order_item orderitems0_ 
  where
        orderitems0_.order_id in ( ?, ? )
 ----------------------------------------------------------------------        
  -> from order_item orderitems0_ where orderitems0_.order_id in (4, 11);
```
맨 마지막 줄을 보면 order_id 가 4, 11 인 OrderItem 을 불러오는 쿼리가 발생한다. 즉, Orders에 관련된 OrderItem 모두 읽어온다.<br>


### 3. OrderItem 에서 ManyToOne 관계인 Item(one) 에 대한 쿼리 발생

```text
  select
        item0_.item_id as item_id2_3_0_,
        item0_.name as name3_3_0_,
        item0_.price as price4_3_0_,
        item0_.stock_quantity as stock_qu5_3_0_,
        item0_.artist as artist6_3_0_,
        item0_.etc as etc7_3_0_,
        item0_.author as author8_3_0_,
        item0_.isbn as isbn9_3_0_,
        item0_.actor as actor10_3_0_,
        item0_.director as directo11_3_0_,
        item0_.dtype as dtype1_3_0_ 
  from
        item item0_ 
  where
        item0_.item_id in ( ?, ?, ?, ?)
 --------------------------------------------------------------- 
  -> from item item0_ where item0_.item_id in (2, 3, 9, 10);
```

OrderItem 와 관련된 모든 Item 을 읽어오는 쿼리가 발생한다. 이전에는 하나하나 접근했다면 batch size 을 설정했기 때문에 위와 같이 한번에 불러와 쿼리 호출 수를 줄일 수 있다.
<br>

### batch size 설정 시 장점

ToMany 관계에 batch size 설정 시 장점을 정리하면 다음과 같다.

- 연관관계로 관련된 모든 데이터를 한번에 불러오기 때문에 쿼리 호출 수가 ```N + 1```에서 ```1 + 1```로 최적화 된다.
- collection fetch join 하는 경우보다 쿼리 호출이 증가하지만 DB 데이터 전송량이 감소한다.
- 또한, collection fetch join 은 paging 이 불가능하지만 offset, limit 설정으로 페이징이 가능하다.

결론은 ToOne 관계는 fetch join 해도 paging 에 영향을 주지 않으니 fetch join 으로 쿼리 호출 수를 줄이고, ToMany 관계는 batch size 설정으로 최적화한다.
<br>

## where in 로 N + 1 쿼리 해결

- 해당 커밋 [4aedce8](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/4aedce8cbffc72a98584b03014ec44e2b7740d0f)

```java
public List<OrderQueryDto> findOrderQueryDtos() {
    List<OrderQueryDto> result = findOrders();

    result.forEach(o -> {
        List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId());
        o.setOrderItems(orderItems);
    });
    return result;
}
```

```java
private List<OrderItemQueryDto> findOrderItems(Long orderId) {
	return em.createQuery(
    		"select new evelyn.ordersystem.repository.order.query
            .OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
    		" from OrderItem oi" +
        	" join oi.item i" +
        	" where oi.order.id = : orderId", OrderItemQueryDto.class)
     	.setParameter("orderId", orderId)
        .getResultList();
}
```

- findOrders() : Order 와 **ToOne** 관계인 ```Member```, ```Delivery``` 엔티티는 join 으로 한번에 조회
- findOrderItems() : Order와 **ToMany** 관계인 ```OrderItem``` 을 join하면 최적화가 어려우니 각각 조회하도록 별도의 method 에서 조회


### 1. 모든 order 를 조회하는 쿼리 발생

```text
	select
        order0_.order_id as col_0_0_,
        member1_.name as col_1_0_,
        order0_.order_date as col_2_0_,
        order0_.status as col_3_0_,
        delivery2_.city as col_4_0_,
        delivery2_.street as col_4_1_,
        delivery2_.zipcode as col_4_2_ 
    from
        orders order0_ 
    inner join
        member member1_ 
            on order0_.member_id=member1_.member_id 
    inner join
        delivery delivery2_ 
            on order0_.delivery_id=delivery2_.delivery_id
```

### 2. ToMany 관계인 OrderItem 을 조회하는 쿼리 발생

```text
	select
        orderitem0_.order_id as col_0_0_,
        item1_.name as col_1_0_,
        orderitem0_.order_price as col_2_0_,
        orderitem0_.count as col_3_0_ 
    from
        order_item orderitem0_ 
    inner join
        item item1_ 
            on orderitem0_.item_id=item1_.item_id 
    where
        orderitem0_.order_id=?
```

### 3. ToMany 관계인 OrderItem 을 조회하는 쿼리 발생

```
   select
        orderitem0_.order_id as col_0_0_,
        item1_.name as col_1_0_,
        orderitem0_.order_price as col_2_0_,
        orderitem0_.count as col_3_0_ 
    from
        order_item orderitem0_ 
    inner join
        item item1_ 
            on orderitem0_.item_id=item1_.item_id 
    where
        orderitem0_.order_id=?
```

ToOne 관계는 join 해도 row 수가 증가하지 않으므로 ```Member```, ```Delivery``` 는 함께 조회하는 루트 쿼리 1개가 발생한다.

ToMany 관계를 join 하면 row 수가 증가해 최적화가 어렵기 때문에 별도의 method 를 작성했지만, 모든 OrderItem 을 조회하는 **N 번의 쿼리가 발생**되므로 ```N + 1 문제```가 발생한다. 모든 OrderItem 을 조회하기 위해 N 번 쿼리가 발생하는 문제를 최적화하기 위해 **where 절에 in을 추가**한다.
<br>



- 해당 커밋 [3f29696](https://github.com/evelyn82ny/JPA-N-plus-1-query-problem/commit/3f29696f0723b2603653dfbf9804abca7762fe47)

```where oi.order.id = : orderId``` 에서 ```where oi.order.id in :orderIds``` 으로 변경했다.

### 1. 모든 Order 를 조회하는 쿼리 발생

```text
 	select
        order0_.order_id as col_0_0_,
        member1_.name as col_1_0_,
        order0_.order_date as col_2_0_,
        order0_.status as col_3_0_,
        delivery2_.city as col_4_0_,
        delivery2_.street as col_4_1_,
        delivery2_.zipcode as col_4_2_ 
    from
        orders order0_ 
    inner join
        member member1_ 
            on order0_.member_id=member1_.member_id 
    inner join
        delivery delivery2_ 
            on order0_.delivery_id=delivery2_.delivery_id
```

### 2. ToMany 관계인 OrderItem 를 한번에 조회하는 쿼리

```text
 	select
        orderitem0_.order_id as col_0_0_,
        item1_.name as col_1_0_,
        orderitem0_.order_price as col_2_0_,
        orderitem0_.count as col_3_0_ 
    from
        order_item orderitem0_ 
    inner join
        item item1_ 
            on orderitem0_.item_id=item1_.item_id 
    where
        orderitem0_.order_id in (
            ? , ?
        )
--------------------------------------------------------       
 -> where orderitem0_.order_id in (4 , 11);
```

ToMany 관계인 OrderItem 을 한번에 조회하기 위해 **where 절에 in 을 추가**했더니 총 2개의 쿼리가 발생했다. 즉, ```where in``` 으로도 **N + 1** 문제를 **1 + 1** 로 해결할 수 있다.