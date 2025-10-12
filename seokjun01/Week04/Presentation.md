# 4주차 발표 주제: 프록시 패턴과 Spring AOP의 관계

## 목차 
1. 프록시 패턴이란
2. 스프링AOP는 뭐고 어떻게 사용될까?
3. 프록시 패턴과 Spring AOP의 관계
4. 정리

### 프록시 패턴(Proxy Pattern) 이란?

정의:
프록시 패턴은 어떤 객체에 대한 접근을 제어하기 위해 그 객체의 대리인(Proxy)을 두는 디자인 패턴이다.
클라이언트는 진짜 객체(Real Subject)를 직접 사용하지 않고, 프록시 객체를 통해 접근한다.
강의에서는 프록시 객체를 통해 동기화를 걸었다. (기억나시죠 ?)


구조:
- Subject(인터페이스): 실제 객체와 프록시가 공통으로 구현해야 하는 인터페이스
- RealSubject: 실제 핵심 기능을 수행하는 객체
- Proxy: RealSubject를 대신해 요청을 받고, 부가 기능을 수행한 뒤 RealSubject에 위임

동작 예시:

// 1. 공통 인터페이스
public interface Service {
    void doWork();
}

// 2. 실제 객체
public class RealService implements Service {

    @Override
    public void doWork() {
    System.out.println("실제 핵심 로직 수행");
    }
}

// 3. 프록시 객체
public class ServiceProxy implements Service {

    private final RealService realService = new RealService();

    @Override
    public void doWork() {
        System.out.println("[로그] 작업 시작");
        realService.doWork(); // 실제 객체 호출
        System.out.println("[로그] 작업 종료");
    }
}

// 실행
public class Main {
public static void main(String[] args) {
    Service service = new ServiceProxy();
    service.doWork();
    }
}

결과:
[로그] 작업 시작
실제 핵심 로직 수행
[로그] 작업 종료

핵심 로직을 수정하지 않고 공통 기능(로깅, 트랜잭션 등)을 대리 객체에서 추가 가능.

### Spring AOP (Aspect Oriented Programming)

개념:
AOP는 핵심 기능(Core Concern)과 부가 기능(Cross-cutting Concern)을 분리하여 관리하는 프로그래밍 패러다임이다.

핵심 기능과 부가 기능의 차이
핵심 기능 : 핵심 로직, 비즈니스 로직
부가 기능 : 트랜재셕, 로깅, 보안, 예외처리 등등 ..

왜 필요한가?
- 로깅, 트랜잭션 관리, 보안 검증, 실행 시간 측정 등 반복되는 공통 기능
- 모든 클래스에 직접 작성하면 코드 중복 + 유지보수 어려움
- AOP는 부가 기능을 한 곳에서 정의하고, 원하는 메서드 실행 전후에 자동 삽입

핵심 개념:

| 용어          | 설명                                         | 스프링 어노테이션 / 역할                                           |
|---------------|--------------------------------------------|------------------------------------------------------------------|
| **Aspect**    | 부가 기능을 담은 클래스                     | `@Aspect` → AOP용 클래스임을 표시                                  |
| **Join Point**| 부가 기능이 삽입될 수 있는 위치             | 어노테이션 없음, 주로 메서드 호출 시점                              |
| **Advice**    | 실제 실행될 부가 기능 코드                  | `@Before`, `@After`, `@Around`, `@AfterReturning`, `@AfterThrowing` |
| **Pointcut**  | 부가 기능 적용 범위(메서드/클래스) 지정     | `@Pointcut("execution(...)")` → 적용 대상 표현식                   |
| **Proxy**     | 스프링이 자동 생성하는 대리 객체             | 어노테이션 없음, `@Component` 등으로 등록된 Bean이 Proxy로 생성     |

AOP 동작 방식:
1. 스프링 컨테이너가 Bean을 생성할 때 AOP가 적용된 클래스는 Proxy 객체 생성
2. 클라이언트가 메서드 호출 → Proxy가 먼저 받아 Advice 실행 → 실제 메서드 호출 → 종료 후 다시 Advice 실행
3. 즉, 내부적으로 프록시 패턴 기반으로 동작

AOP 적용 예시:

@Aspect
@Component
public class LoggingAspect {

    @Pointcut("execution(* com.example.controller.*.*(..))")
    private void controllerMethods() {}

    @Before("controllerMethods()")
    public void beforeLog(JoinPoint joinPoint) {
        System.out.println("[LOG] 메서드 시작: " + joinPoint.getSignature().getName());
    }

    @After("controllerMethods()")
    public void afterLog(JoinPoint joinPoint) {
        System.out.println("[LOG] 메서드 종료: " + joinPoint.getSignature().getName());
    }
}

설명:
- @Aspect → AOP용 클래스, 공통 기능을 메서드 전/후에 자동 삽입
- @Component → 스프링 빈으로 등록
- @Pointcut → 적용 대상 메서드 지정
- @Before, @After → 메서드 실행 전/후 Advice

실행 흐름:
Controller 메서드 호출
↓
스프링이 생성한 Proxy가 가로챔
↓
@Before 실행 (로그 시작)
↓
실제 Controller 로직 실행
↓
@After 실행 (로그 종료)

개발자는 핵심 비즈니스 로직만 집중, 공통 기능은 Aspect에서 관리

### 프록시 패턴과 AOP의 관계

비교 항목 | 프록시 패턴 | Spring AOP
--------|-------------|----------
구현 방식 | 개발자가 직접 프록시 작성 | 스프링이 런타임에 자동 생성 (JDK 동적 프록시, CGLIB)
목적 | 접근 제어 / 기능 확장 | 공통 관심사 분리 (로깅, 트랜잭션 등)
특징 | 코드 중복 발생 가능 | 프록시 자동 생성, 유지보수 용이
공통점 | 실제 객체 대신 프록시가 호출 가로챔 | AOP 내부 동작이 프록시 기반

###  정리

핵심 포인트 | 설명
-----------|-----
프록시 패턴 | 객체 접근 제어 및 부가 기능 추가 디자인 패턴
AOP | 프록시 패턴 확장, 공통 관심사 분리 프로그래밍
Spring AOP 작동 원리 | 스프링이 Bean 생성 시 자동 Proxy 생성, 부가 기능 삽입
결과 | 코드 중복 감소, 유지보수 용이, 핵심 로직 집중 가능


