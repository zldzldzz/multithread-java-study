## 1. Synchronized 의 한계와 또 다른 동기화 방법 (non-block, Atomic)
<문제 상황>
멀티스레드 환경에서는 여러 스레드가 동시에 같은 자원(변수, 객체)에 접근하면 동시성 문제가 생긴다.
예를 들어 은행 계좌에서 두 명이 동시에 출금하면 잔액이 꼬일 수 있다.

기존 해결 방법
자바는 synchronized 키워드로 임계 영역(critical section)을 만들고, 한 번에 한 스레드만 공유자원에 접근하도록 막는다.

문제점
* 모든 스레드가 락(lock)을 기다려야 해서 성능 저하 발생
* 락 경쟁이 많아지면 CPU는 놀고, 스레드는 줄 서서 기다리므로 병목(bottleneck) 발생
* 단순히 카운터 1 증가 같은 가벼운 작업에도 락을 쓰는 건 오버스펙이다
* 성능 저하의 경우 : 단순 읽기 , 너무 넓은 범위의 코드블록을 , 교착상태
* 교착상태 (Deadlock) : 스레드1은 A.methodA()를 실행하면서 A에 락을 걸고, 이어서 B.methodB()를 호출하려 함 그에 비해 스레드2는 B.methodB()를 실행하면서 B에 락을 걸고, 이어서 A.methodA()를 호출하려 함

대안
그래서 Non-blocking 방식의 Atomic 클래스가 등장했다.
락을 쓰지 않고도 동시성을 안전하게 보장한다.

## 2. 왜 필요한가? (락을 사용할 시에 생기는 성능저하)
락 기반 동기화의 문제점
* synchronized는 락을 얻기 위해 컨텍스트 스위칭이 발생 → 스레드 전환 오버헤드
* 락 경합이 심하면 CPU 사용률은 높지만 실제 작업은 거의 이루어지지 않는다
* 단순한 count++ 연산에도 락을 쓰면 낭비가 크다

비유
화장실이 10개 있는데, 각 칸마다 문을 잠그지 않고 전체 입구에 큰 자물쇠를 거는 꼴이다.
한 명만 들어가고, 나머지는 줄 서야 한다.
실제로는 10명이 동시에 써도 문제없을 수도 있는데 말이다.

그래서 락 대신, 더 가볍고 빠른 방법이 필요하다. 그 방법이 바로 Atomic 클래스이다.

## 3. Atomic의 핵심기술: CAS (Compare And Swap)
아이디어
지금 내가 가진 값이 기대한 값(expected)과 같으면 새로운 값으로 바꿔라.

동작 방식
1. 메모리 (힙)에서 변수 값을 읽는다 (current)
2. 예상한 값(expected)과 비교한다
3. 같으면 새로운 값(newValue)로 교체한다
4. 다르면 실패하고 다시 시도한다 (재시도 루프)

예시 (카운터 증가)
* count = 5
* 내가 예상한 값 = 5
* CPU에게 "만약 5라면 6으로 바꿔줘"라고 요청 → 성공 → count = 6
* 만약 누군가 먼저 바꿔서 값이 6이라면, "내 예상이 틀렸다" → 실패 → 다시 읽고 재시도

이 방식은 락을 쓰지 않고도 안전하게 값을 바꿀 수 있다. 그래서 Non-blocking 동기화라고 부른다.

추가 설명: CAS의 문제점과 해결책
CAS는 값이 변했는지만 검사한다.
따라서 값이 A → B → A로 바뀌면, 실제로는 변경이 있었지만 CAS는 변경이 없었다고 판단해 성공할 수 있다. 이것이 ABA 문제다.
해결책으로는 버전 번호를 붙이는 방식이 있으며, 자바에서는 AtomicStampedReference와 AtomicMarkableReference를 제공해 이를 보완한다.
(이거까지는 파고들지 않겠음 ... 대충 버전으로 관리한다라는 의미 ..)

## 4. Atomic 클래스들 설명 + 사용법
자바는 java.util.concurrent.atomic 패키지에서 다양한 Atomic 클래스 제공한다.
* AtomicInteger: int 값을 원자적으로 다룸
   * 메서드: get(), set(value), incrementAndGet(), decrementAndGet(), addAndGet(delta), compareAndSet(expect, update)
* AtomicLong: long 타입 버전
* AtomicBoolean: true/false 플래그
* AtomicReference<T>: 객체 레퍼런스도 CAS로 안전하게 다룸
* AtomicStampedReference, AtomicMarkableReference: CAS의 ABA 문제 해결용

사용 예시
AtomicInteger count = new AtomicInteger(0);
// 값 가져오기
count.get(); // 0
// 값 변경
count.set(5); // count = 5
// 증가
count.incrementAndGet(); // 6
// 비교 후 교체
count.compareAndSet(6, 10); // true 반환 후 count = 10

## 5. 장단점
장점
* 락을 안 쓰므로 빠르다 (특히 다중 코어 환경에서 효율적)
* 스레드가 블록되지 않는다 → 교착 상태(deadlock)가 발생하지 않는다
* 단순한 값 변경(카운터, 플래그)에 최적화되어 있다

단점
* 단일 변수만 안전하게 다룰 수 있다
* CAS는 실패 시 재시도가 필요하다 → 경쟁이 심할 경우 성능 저하 가능
* 복잡한 로직(출금/입금 같이 여러 단계 작업)에는 부적합하다

## 6. 언제 Synchronized? 언제 Atomic?
Atomic (Non-blocking) 사용이 적합한 경우
* 단순 카운터, 플래그
* 요청 수, 접속자 수, 이벤트 횟수 같은 통계성 값
* Boolean 상태 (예: 서비스 켜짐/꺼짐 플래그)

Synchronized (락) 사용이 적합한 경우
* 여러 연산을 하나로 묶어야 할 때 (예: 계좌 출금처럼 확인 + 차감)
* 컬렉션, 리스트, 맵 같이 복잡한 객체 다룰 때
* 비즈니스 로직에서 여러 값의 일관성을 동시에 유지해야 할 때

정리
* synchronized: 확실하지만 무겁고 느리다
* Atomic: 가볍고 빠르지만, 단순 연산에 적합하다
* 실무에서는 두 가지 모두 사용한다.
   * 로그 카운트, 접속자 수 → AtomicInteger
   * 트랜잭션, 계좌 출금 → synchronized
