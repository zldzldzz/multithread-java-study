# 📚 10주차 - 동시성 컬렉션과 프록시 패턴

## 1. Java `util.collection` 동시성 문제

- 일반적인 `java.util` 컬렉션(`ArrayList`, `HashMap` 등)은 **스레드 안전하지 않음**.
- 여러 스레드가 동시에 접근하면 **데이터 손실(Corruption)**이나 **예외(ConcurrentModificationException)** 발생 가능.
- 따라서 멀티스레드 환경에서는 별도의 동기화 처리 필요.

---

## 2. 스레드 안전(thread-safe)

- **스레드 안전(thread-safe)**: 여러 스레드가 동시에 접근해도 프로그램이 올바르게 동작하는 성질.
- 컬렉션 예시:
    - `Collections.synchronizedList(new ArrayList<>())`
    - `ConcurrentHashMap`, `CopyOnWriteArrayList` 등
- 특징
    - `synchronizedList`는 모든 메서드에 락이 걸려 단일 스레드 접근 보장.
    - `ConcurrentHashMap`은 락을 세분화하여 **성능과 안전**을 동시에 보장.

---

## 3. 프록시(Proxy) 개념

- **프록시 패턴**: 실제 객체 앞에 대리 객체를 두어 접근을 제어.
- 활용 예:
    - 접근 제어, 로깅, 트랜잭션 관리
- Java 예시: `Collections.synchronizedList()` 내부 구현이 **프록시 패턴 기반**임.
      - 스레드 안전을 위해 컬렉션 접근에 락을 추가하는 래퍼도 일종의 프록시

---

## 4. 극한의 프록시: Spring AOP

- Spring에서 **AOP(Aspect-Oriented Programming)**는 프록시 패턴을 극한으로 활용한 사례.
- 특징:
    - 핵심 로직(Core) 앞뒤에 부가 기능(Advice)을 적용.
    - 트랜잭션, 로깅, 보안 등 공통 관심사 처리 가능.
    - 정리하면, Spring AOP는 “프록시 패턴 + 부가 기능”의 실전 적용 사례임.
    - 원래 객체를 변경하지 않고, **프록시를 통해 기능 확장**.

---

## 5. 정리

1. `java.util` 기본 컬렉션은 **동시성 안전하지 않음**.
2. **스레드 안전 컬렉션** 사용: `synchronizedList`, `ConcurrentHashMap`, `CopyOnWriteArrayList`. 등등 ..
3. **프록시 패턴**은 실제 객체 앞에 대리 객체를 두어 접근을 제어.
4. **Spring AOP**는 프록시 패턴을 극한으로 활용한 사례로, 트랜잭션, 로깅, 보안 등을 쉽게 적용 가능.
5. 멀티스레드 환경에서는 프록시 패턴이나 스레드 안전 컬렉션을 적절히 활용해야 안정적인 코드 작성 가능.
