# 스프링에서의 프록시 패턴

Spring에서는 프록시 패턴이 많이 사용되고 있다. 

프록시 패턴을 사용하는 이유?

- 접근 권한을 부여할 수 있다.
- 부가 기능을 추가할 수 있다.

스프링이 자동으로 만들어주는 프록시 객체는 반드시 스프링 IoC 컨테이너에 의해 관리되는 빈이어야 한다.

- 왜냐하면 스프링에서 프록시가 만들어지는 과정은 스프링 컨테이너가 빈을 생성하고 초기화하는 생명주기 안에서 일어나기 때문이다.
- 빈이 아닌 `new`로 직접 생성한 객체는 스프링 IoC 컨테이너의 생명주기를 타지 않기 때문에 스프링은 이 객체가 생성되었는지조차 알지 못한다. 따라서 프록시 객체로 만들어지지 않는다.

하지만, 빈이라고 모두 프록시 객체가 만들어지는 것이 아니다.

스프링은 ‘어떤 빈의 메서드 호출에 중간에 개입해서 부가적인 기능을 덧붙여야 할 때’ 프록시 객체를 만든다.

# 스프링이 프록시 객체로 만드는 경우

## 1. AOP 어노테이션이 붙는 경우

- `Transactional` : 트랜잭션 처리
- `@Cacheable` : 캐싱 처리
- `@Async` : 비동기 처리
- `@PreAuthorize` , `@Secured` : 메서드 레벨 보안

이들은 모두 메서드 호출 전후에 공통적인 부가 기능을 실행해야 하므로 프록시가 필요하다.

즉, 스프링이 IoC 컨테이너를 구성할 때, `@Transactional`, `@Cacheable`, `@Async`등 **AOP 어노테이션이 붙은 빈**을 발견하면, 그 빈의 **프록시 객체**를 자동으로 만들어서 등록한다.

## 2. 스코프 빈(Scoped Bean)을 주입받는 경우

```cpp
@Service // 싱글톤으로 하나만 생성됨
public class SingletonService {

    // HTTP 요청마다 새로 생성되는 객체를 주입받아야 함
    @Autowired
    private RequestScopedBean requestBean;

    public void logic() {
        // 매번 다른 요청에 맞는 requestBean을 사용해야 함!
        requestBean.process();
    }
}

@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedBean {
    // ...
}
```

- `SingletonService`는 애플리케이션 시작 시 딱 한 번만 만들어진다.
    - 싱글톤 빈은 애플리케이션 시작 시 한 번만 생성되기 때문에 그 시점에는 “HTTP 요청”이 없다.
- 하지만 `RequestScopedBean`은 HTTP 요청이 올 때마다 새로 만들어져야 한다.

이 문제를 해결하기 위해 스프링은 `RequestScopedBean` 에 가짜 프록시 객체를 주입한다. 

- `ScopedProxyMode.TARGET_CLASS`

## 3. 지연 로딩(`@Lazy`)

- `@Lazy`는 해당 빈의 실제 객체 생성을 지연시키는 기능이다.
- 이때 스프링은 지연 로딩 프록시를 대신 주입하여 실제로 메서드가 처음 호출되는 시점에 진짜 객체를 생성한다.

# 프록시 객체 생성 과정

1. 스프링 컨테이너가 빈(Bean)을 생성한다.
- `@Component`, `@Service` 등이 붙은 클래스를 찾아 객체(인스턴스)를 만든다.
1. 빈 후처리기(`BeanPostProcessor`)가 개입한다.
- 스프링은 생성된 빈 객체를 컨테이너에 최종 등록하기 전에, 여러 ‘후처리기’를 통해 추가 작업을 할 기회를 준다.
1. 프록시 생성 여부를 판단하고 실행한다.
- 후처리기는 방금 생성된 빈 객체를 검사하여 `@Transactional` 같은 AOP 관련 어노테이션이 있는지, 또는 스코프 프록시가 필요한지 등을 확인한다.
- 만약 필요하다고 판단되면,  원본 객체를 감싼 프록시 객체를 동적으로 생성한다.
1. 컨테이너에는 원본 대신  프록시 객체가 등록된다.
- 후처리기는 원본 객체 대신 새로 만든 프록시 객체를 컨테이너에 반환한다.

# 프록시가 클래스 전체를 감싼다?

프록시가 만들어지면 실제로는 “클래스 전체를 상속 or 구현해서” 새로운 클래스를 만든다.

(예: `MyService$$SpringCGLIB$$0`) 

즉, 물리적으로는 클래스 전체를 감싸는 게 맞다.

하지만, “클래스 전체를 감싼다” ≠ “모든 메서드를 가로챈다.”

실제 AOP 어드바이스는 매 호출 시점에 매칭 검사를 해서, 포인트컷에 해당되는 메서드만 가로채서 실행한다. 

## 예시

```java
@Service
public class UserService {

    @Transactional
    public void save() { ... }

    @Cacheable("user")
    public User find(Long id) { ... }

    public void print() { ... }
}

```

스프링이 내부적으로 만드는 구조는 밑의 의사코드와 같다.

```java
UserService$$SpringCGLIB$$0 extends UserService {
    
    @Override
    public void save() {
        // TransactionInterceptor 동작
        beginTransaction();
        super.save();
        commitOrRollback();
    }

    @Override
    public User find(Long id) {
        // CacheInterceptor 동작
        if (cacheHit()) return cached;
        User u = super.find(id);
        cachePut(u);
        return u;
    }

    @Override
    public void print() {
        // 포인트컷 매칭 X
        super.print();
    }
}

```

`save()` 에 `@Transactional`이 매칭되어 트랜잭션 프록시가 개입한다.

`find()` 에 `@Cacheable`이 매칭되어 캐시 프록시가 개입한다.

따라서 `save()` 와 `find()`는 모두 각자에 맞는 어드바이스를 실행한 후 `super.xxx()` 원래 메서드를 호출한다.

하지만 `print()`는 AOP 어노테이션이 붙어있지 않았기 때문에 포인트컷 매칭이 되지 않는다.

→ 결과적으로 print()는 아무런 행동도 하지 않고 `super.xxx()` 를 호출한다.
