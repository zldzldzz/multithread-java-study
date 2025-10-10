# 📚 3주차 - 섹션 7

## 1. Synchronized의 단점
- **무한 대기**
    - 락이 풀릴 때까지 무한 대기한다.
    - 중간에 인터럽트가 걸려도 빠져나올 수 없다.
    - 특정 시간만 대기하는 **타임아웃** 기능이 없다.
- **공정성 부족**
    - 락이 풀려도 어떤 스레드가 락을 획득할지 확신할 수 없다.

---

## 2. LockSupport
- 스레드를 **waiting 상태**로 변경하는 저수준 API.
- 주요 메서드:
    - `park()` : 스레드를 waiting 상태로 변경
    - `parkNanos(long nanos)` : 지정된 나노초 동안 waiting 상태 유지
    - `unpark(Thread t)` : 지정된 스레드를 깨움

### 상태 차이
- **Blocked**
    - 락을 기다리는 상태.
    - 인터럽트가 걸려도 즉시 빠져나오지 못하고 여전히 Blocked 상태.
- **Waiting**
    - 다른 스레드가 깨워줄 때까지 대기.
    - 인터럽트가 걸리면 waiting 상태를 빠져나올 수 있음.
- **Timed_Waiting**
    - Waiting 상태에 시간 제한이 포함된 것.

### 특징
- `synchronized`의 **무한 대기 단점**을 해결할 수 있다.
- 하지만 LockSupport는 **너무 저수준**의 기능만 제공한다.
    - 직접 락 획득/해제 로직을 구현해야 한다.
- 보다 고수준의 API가 필요 → **ReentrantLock** 사용.

---

## 3. ReentrantLock - 개요
- `Lock` 인터페이스의 대표적인 구현체.
- 동시성 프로그래밍에서 안전한 임계 영역 보호를 위해 사용.

### 주요 메서드
- `void lock()`
    - 락을 획득한다.
    - 다른 스레드가 락을 보유 중이면 waiting 상태로 들어간다.
- `void lockInterruptibly() throws InterruptedException`
    - 락 획득을 시도하되, 인터럽트에 반응할 수 있다.
- `boolean tryLock()`
    - 즉시 락 획득을 시도하고, 성공 여부를 반환.
- `boolean tryLock(long time, TimeUnit unit) throws InterruptedException`
    - 주어진 시간 동안만 락 획득을 시도한다.
- `void unlock()`
    - 락을 해제한다. (락을 획득한 스레드만 호출 가능)
- `Condition newCondition()`
    - 조건 변수를 생성한다. (세밀한 스레드 제어에 사용)

---

## 4. 공정성 문제 해결
- ReentrantLock은 스레드가 **공정하게 락을 획득**할 수 있는 모드를 제공한다.
- **비공정 모드 (기본값)**
    - 락 획득 속도가 빠르다.
    - 선점 가능 → **기아 현상** 발생 가능.
- **공정 모드**
    - 오래 기다린 스레드가 우선적으로 락을 획득한다.
    - 성능은 다소 느려지지만 **공정성 보장**.

---

## 5. 대기 중단 기능
- `tryLock()`
    - 락을 획득하지 못하면 즉시 반환한다.
    - 무한 대기를 피할 수 있음.

---
