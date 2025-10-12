## Lock-Free 알고리즘이란?

### 1. 개요

멀티스레드 환경에서는 여러 스레드가 동시에 같은 데이터를 수정하려 할 때 데이터 일관성 문제가 발생한다.

이 문제를 해결하기 위해 일반적으로 락(Lock)을 사용하지만, 락은 새로운 문제를 낳기도 한다.

따라서 등장한 개념이 바로 Lock-Free(락 프리) 알고리즘이다.

락을 사용하지 않으면서도 데이터 일관성을 유지하고, 스레드 간 경쟁을 효율적으로 해결하는 기법이다.

---

### 2. 락 기반 알고리즘이란

락(Lock) 기반 알고리즘은 공유 자원에 접근할 때 한 번에 하나의 스레드만 접근하도록 제어하는 방식이다. 대표적인 예는 synchronized 블록이나 ReentrantLock이다.

```java
synchronized(lock) {
    count++;
}
```

락을 획득한 스레드는 안전하게 임계 구역에 접근할 수 있고, 다른 스레드는 락이 해제될 때까지 대기 상태에 머무른다.

---

### 3. 락 기반 알고리즘의 문제점

락은 안전하지만, 다음과 같은 성능과 안전성 문제를 야기한다.

1. 병목 현상
    1. 여러 스레드가 동시에 락을 기다리면, CPU는 대기 스레드가 많아도 일을 하지 못한다.
2. 교착 상태
    1. 스레드 A, B가 서로의 락을 기다리며 영원히 멈춰버리는 상황 발생.
3. 우선순위 역전
    1. 낮은 우선순위 스레드가 락을 점유하면, 높은 우선순위 스레드가 기다려야 하는 상황
4. 컨텍스트 스위칭 오버헤드
    1. 락을 기다리며 블록된 스레드는 CPU 스케줄러에 의해 계속 교체되어 비용이 증가한다.

---

### 4. 락 프리(Lock-Free)란?

Lock-Free(락 프리) 알고리즘은 락을 전혀 사용하지 않고도 원자적 연산(atomic operation) 만으로 공유 데이터를 안전하게 변경하는 방식이다.

핵심 기술은 CAS(Compare-And-Swap)이다.

```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // 내부적으로 CAS 사용
```

CAS는 아래와 같이 동작한다.

1. 현재 값이 예상한 값(expected)와 같은지 비교.
2. 같다면 새로운 값으로 교체
3. 다르다면 실패하고 다시 시도(스핀)

이 과정을 통해 락 없이도 데이터의 일관성을 유지할 수 있다.

---

### 5. 락 프리 자료구조의 장점

1. **병목 현상이 줄어듦**
    1. 락이 없으므로 스레드 간 대기 시간이 거의 없음
    2. 실패한 스레드는 재시도(spin)만 하므로 CPU를 계속 활용 가능
2. **교착 상태(Deadlock) 없음**
    1. 락을 걸지 않기 때문에 서로 기다리는 상태 자체가 존재하지 않음
3. 성능 향상
    1. ConcurrentLinkedQueue, AtomicInteger, LongAdder 등은 CAS 기반으로 구현되어 높은 동시 처리 성능을 제공

---

### 6. 락 프리 자료구조의 단점

### (1) 스핀락으로 인한 CPU 낭비

CAS는 실패 시 계속 루프를 돌며 재시도 한다.

경쟁이 심한 환경에서는 CPU 자원을 과도하게 사용할 수 있다. 이를 busy-waiting이라고 함

**스핀락으로 인한 CPU 낭비 문제 해결 방법**

**Back-off 전략**

→ 재시도 사이제 짧은 시간 Thread.yield() 혹은 지연을 줘서 CPU 낭비를 감소

###  🌟 (2) ABA 문제

CAS 연산의 가장 대표적인 약점.

CAS는 단순히 “값이 같은가?” 만 비교하기 때문에, 아래와 같은 상황이 발생할 수 있음.

```java
스레드1: 값 A를 읽음
스레드2: A → B → A로 변경
스레드1: 값이 여전히 A라서 변경 성공 (실제로는 중간에 B가 존재하지만 알 수 없음)
```

이렇게 되면 스레드 1은 값이 바뀌지 않았다고 착각하고 잘못된 업데이트를 수행할 수 있음.

### 🚀ABA 문제 해결 방법

**AtomicMarkableReference**

→ 값의 변경 여부만 빠르게 추적(스탬프 대신 boolean flag 사용)  
→ 여기서 스탬프란 값의 변경 이력을 추적하기 위해 사용하는 추가 정보(버전 번호)이다. AtomicStampedReference 에서 사용됨


> 락 기반 알고리즘은 “순서를 보장하는 안전한 방법”이고,
락 프리 알고리즘은 “경쟁을 최소화한 빠른 방법”이다.
대신 락 프리는 CAS 실패 시 재시도와 ABA 문제에 대한 주의가 필요하다.
>


```java
package thread.cas.aba;

import java.util.concurrent.atomic.AtomicInteger;
import static util.ThreadUtils.sleep;

// ABA 문제를 재현하기 위한 예제 (AtomicInteger 사용)
// ThreadA : 100 -> 50 변화 (changeA로 메서드 분리)
// ThreadB : 100 -> 0 -> 100 변화 (changeB로 메서드 분리)
public class CasABAMainV1 {

    public static void main(String[] args) {
        System.out.println("[1] ABA_V1 with AtomicInteger");

        // 초기 잔액: 100원
        AtomicInteger balance = new AtomicInteger(100);
        System.out.println("start balance = " + balance.get());
        Thread t1 = new Thread(() -> changeA(balance), "T1");
        Thread t2 = new Thread(() -> changeB(balance), "T2");

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // T1: 잔액이 100이면 50으로 바꾸는 CAS 시도
    private static void changeA(AtomicInteger balance) {
        int read = balance.get();
        System.out.println(Thread.currentThread().getName() + ": read balance = " + read);
        sleep(1000); // 일부러 지연
        boolean result = balance.compareAndSet(100, 50);
        System.out.println(Thread.currentThread().getName()
            + ": CAS(100->50) result = " + result + ", final = " + balance.get());
    }

    // T2: 100 -> 0 -> 10 으로 바꿈
    private static void changeB(AtomicInteger balance) {

        // 상태 값 상수
        final int A_VALUE = 100;
        final int B_VALUE = 0;
        final int C_VALUE = 100;

        sleep(300);
        boolean step1 = balance.compareAndSet(A_VALUE, B_VALUE);
        if (step1) {
            System.out.println(Thread.currentThread().getName() + ": A(" + A_VALUE + ") -> B(" + B_VALUE + ")");
        } else {
            System.out.println(Thread.currentThread().getName()
                + ": A(" + A_VALUE + ") -> B(" + B_VALUE + ") FAILED, current=" + balance.get());
        }

        sleep(300);
        boolean step2 = balance.compareAndSet(B_VALUE, C_VALUE);
        if (step2) {
            System.out.println(Thread.currentThread().getName() + ": B(" + B_VALUE + ") -> C(" + C_VALUE + ")");
        } else {
            System.out.println(Thread.currentThread().getName()
                + ": B(" + B_VALUE + ") -> C(" + C_VALUE + ") FAILED, current=" + balance.get());
        }
    }
}

```



### CAS 잘 작동했을 때 출력 값  
changeA: ThreadA : 100 -> 50  
changeB: ThreadB : 100 -> 0 -> 10  

```
[1] ABA_V1 with AtomicInteger
start balance = 100
T1: read balance = 100
T2: A(100) -> B(0)
T2: B(0) -> C(10)
T1: CAS(100->50) result = false, final = 10
```

### ABA 문제가 발생했을 때  
ThreadA : 100 -> 50   
ThreadB : 100 -> 0 -> 100   

```
[1] ABA_V1 with AtomicInteger
start balance = 100
T1: read balance = 100
T2: A(100) -> B(0)
T2: B(0) -> C(100)
T1: CAS(100->50) result = true, final = 50
```


```java
package thread.cas.aba;

import java.util.concurrent.atomic.AtomicMarkableReference;
import static util.ThreadUtils.sleep;

// ABA 문제 해결 예제 (AtomicMarkableReference 사용)
public class CasABAMainV2 {

    public static void main(String[] args) {
        System.out.println("[2] ABA_V2 with AtomicMarkableReference");

        // 초기값: (100, mark=false)
        AtomicMarkableReference<Integer> balance = new AtomicMarkableReference<>(100, false);
        boolean[] holder = new boolean[1];
        System.out.println("start (value,mark) = (" + balance.get(holder) + "," + holder[0] + ")");

        // T1 / T2 작업을 메서드로 분리
        Thread t1 = new Thread(() -> changeA(balance), "T1");
        Thread t2 = new Thread(() -> changeB(balance), "T2");

        t1.start();
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    // T1: (expectedValue=100, expectedMark=false) → (50, true) 시도
    private static void changeA(AtomicMarkableReference<Integer> balance) {
        boolean[] t1holder = new boolean[1]; // mark 상태를 담을 배열, 초기값 false
        int readVal = balance.get(t1holder); // readVal=100 (초기값), t1holder[0]=false
        boolean readMark = t1holder[0];
        System.out.println("T1(markable): read (" + readVal + "," + readMark + ")"); // readVal=100, readMark=false
        sleep(1000);
        boolean result = balance.compareAndSet(100, 50, false, true);
        balance.get(t1holder);
        System.out.println(
                "T1(markable): CAS(100->50, false->true) result=" + result +
                ", final=(" + balance.getReference() + "," + balance.isMarked() + ")");
    }

    // T2: 100,false -> 0,true -> 100,true (중간 변경 흔적을 mark로 남김)
    private static void changeB(AtomicMarkableReference<Integer> balance) {
        sleep(300);
        boolean step1 = balance.compareAndSet(100, 0, false, true);
        if (step1) {
            System.out.println("T2(markable): (100,false) -> (0,true)");
        } else {
            System.out.println("T2(markable): (100,false) -> (0,true) FAILED, current=(" + balance.getReference() + "," + balance.isMarked() + ")");
        }

        sleep(300);
        // 값은 원복하지만 mark는 유지(true)
        boolean step2 = balance.compareAndSet(0, 100, true, true);
        if (step2) {
            System.out.println("T2(markable): (0,true) -> (100,true)");
        } else {
            System.out.println("T2(markable): (0,true) -> (100,true) FAILED, current=(" + balance.getReference() + "," + balance.isMarked() + ")");
        }
    }
}

```

### ABA 문제 해결 출력 값 - AtomicMarkableReference 사용

```
[2] ABA_V2 with AtomicMarkableReference
start (value,mark) = (100,false)
T1(markable): read (100,false)
T2(markable): (100,false) -> (0,true)
T2(markable): (0,true) -> (100,true)
T1(markable): CAS(100->50, false->true) result=false, final=(100,true)
```