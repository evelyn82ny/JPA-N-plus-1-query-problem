> 주문 시스템을 구현하며 JPA N + 1 쿼리 문제를 해결한다.

![png](/_image/order_system_erd.png)

- [Order 와 Member 관계 : @ManyToOne](#ManyToOne)
- [양방향 관계 @JsonIgnore 설정](#Bidirectional-relationship)
- [지연 로딩에 대한 Type definition error 발생](#Type-definition-error)
- [JPA N + 1 쿼리 문제](#JPA-N-plus-1)

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

## Bidirectional relationship

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

![](/_image/init_orders_table.png)

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