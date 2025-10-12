# 📚 10주차 - CAS (Compare And Swap)와 동기화

---

## CAS의 개념
- Compare And Swap (CAS)는 현재 메모리의 값이 기대한 값(expected)과 같을 때만 새로운 값(newValue)으로 교체하는 원자적(atomic) 연산이다.
- 하드웨어 수준에서 제공되는 락이 없는(lock-free) 동기화 기법이다.
- 멀티스레드 환경에서 synchronized 없이도 안전하게 공유 데이터를 수정할 수 있다.
- AtomicInteger, AtomicBoolean 등의 클래스 내부에서 사용된다.

---

## CAS의 동작 과정
1. 메모리에서 현재 값을 읽는다 → current
2. 그 값이 예상한 값(expected)과 같은지 비교한다.
3. 같다면 새로운 값(newValue)으로 교체한다.
4. 다르면 아무 동작도 하지 않고 false를 반환한다.

예시:
boolean result = atomicInteger.compareAndSet(expected, newValue);

- 성공(true): 기대한 값과 실제 값이 같아서 새로운 값으로 교체함.
- 실패(false): 기대한 값과 달라 교체하지 않음 (즉, 다른 스레드가 값을 이미 바꿈).

---

## Spin Lock과의 관계
- CAS 자체는 단순 비교·교체 연산이지만,
  while 반복문으로 감싸면 스핀락(Spin Lock) 형태가 된다.
- 스핀락은 락을 얻을 때까지 계속 반복(CPU를 바쁘게 돌림) 하는 방식이다.

예시:
while (!lock.compareAndSet(false, true)) {
// 락을 얻을 때까지 반복 (바쁜 대기 상태)
}

- 성공 시: 락 획득 → while 탈출
- 실패 시: 계속 반복 → CPU 자원 소비

---

## CAS의 문제점

(1) 바쁜 대기(Spin Waiting)
- CAS 실패 시 계속 반복하므로 CPU를 점유함.
- 스레드 수가 많거나 충돌이 잦을 때 성능 저하 가능.


---

## 장점
- Lock-free (비블로킹): synchronized처럼 락을 점유하지 않음.
- 빠른 성능: context switching이 일어나지 않음.
- 원자성 보장: 하드웨어 수준에서 원자적으로 수행.

---

## CAS의 활용 예시

(1) 단순 카운터 증가 예시
public int incrementAndGet(AtomicInteger value) {
  int current;
  int next;
  do {
    current = value.get();       // 현재 값 읽기
    next = current + 1;          // 증가된 값 계산
  } while (!value.compareAndSet(current, next)); // CAS 실패 시 재시도
    return next;
}

- CAS 실패 시, 다른 스레드가 값을 바꿨다는 의미이므로 성공할 때까지 반복 재시도.

(2) CAS 기반 스핀락 예시
public class SpinLock {
private final AtomicBoolean lock = new AtomicBoolean(false);

    public void lock() {
        while (!lock.compareAndSet(false, true)) {
            // 락 획득 실패 시 스핀 대기
        }
        System.out.println("락 획득 완료");
    }

    public void unlock() {
        lock.set(false);
        System.out.println("락 반납 완료");
    }
}

- lock.compareAndSet(false, true):  
  현재 lock이 false이면 true로 바꾸고 락 획득 성공.  
  이미 true이면 실패하고 while 반복.

---

## 관련 클래스
- AtomicInteger
- AtomicLong
- AtomicBoolean
- AtomicReference
- AtomicStampedReference (ABA 문제 해결용)

---

## 정리 비교

| 구분 | CAS | synchronized |
|------|-----|--------------|
| 동작 방식 | 기대 값과 현재 값을 비교 후 교체 (비블로킹) | 락을 이용한 블로킹 방식 |
| 원자성 | 하드웨어 수준에서 보장 | JVM이 모니터 락으로 보장 |
| 공정성 | 보장되지 않음 (스핀락 가능성) | 어느 정도 보장 (JVM 스케줄러) |
| 성능 | 충돌 적을 때 빠름 | 스레드 대기 시 느림 |
| 복잡도 | 비교적 구현 복잡 | 사용 간단 |
| 대표 클래스 | Atomic 패키지 | synchronized 키워드 |

---

## 정리
- CAS는 “락 없이” 원자성을 보장하는 기법이다.
- 스핀락은 CAS를 반복문으로 감싸서 구현한 형태이다.
- ABA 문제와 바쁜 대기 같은 단점이 존재하지만,  
  synchronized보다 빠르고 효율적인 비블로킹 동기화 기법이다.
