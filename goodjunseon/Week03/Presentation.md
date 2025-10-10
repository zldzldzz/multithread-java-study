## BlockingQueue


BlockingQueue의 개념, 동작원리, 주요 구현체에 대해 설명합니다.  
주요 구현체중 SynchronousQueue를 활용한 핸드오프(handoff) 예제를 이해합니다.

---

**1. Blocking Queues 이해하기**

BlockingQueue는 **자바에서 스레드 간 안전한 데이터 교환을 지원하는 전문화된 큐 자료구조**입니다.

동기화 문제를 해결하기 위해 직접 synchronized, wait/notify를 사용할 필요 없이, **스레드 안전(thread-safe)** 하게 요소를 추가하거나 제거할 수 있도록 설계되었습니다.

- **인터페이스**: java.util.concurrent.BlockingQueue
- **특징**: 자동 동기화, 블로킹 연산 지원 (put(), take())
- **대표 용도**: 생산자-소비자(Producer-Consumer) 패턴 문제 해결

즉, BlockingQueue는 **보일러플레이트(불필요하게 반복되는 동기화 코드)** 를 제거하고, 간단한 API 호출만으로 스레드 간 협력을 안전하게 구현할 수 있게 해줍니다.

---

**2. BlockingQueue의 동작 원리**

- **삽입 연산**
    - 큐가 가득 차 있으면 put()은 요소가 제거될 때까지 자동으로 블로킹됩니다.
    - offer(e, timeout, unit) 메서드를 사용하면 일정 시간만 대기 후 실패할 수 있습니다.
- **삭제 연산**
    - 큐가 비어 있으면 take()는 요소가 추가될 때까지 블로킹됩니다.
    - poll(timeout, unit) 메서드를 사용하면 일정 시간 대기 후 실패할 수 있습니다.
- **메모리 가시성 보장**
    - put()으로 삽입한 쓰기 연산은 take()로 읽을 때 반드시 반영되어 보입니다.
    - 즉, 스레드 간 값 전달 시 **Java Memory Model(JMM)** 규칙(happens-before)을 보장합니다.

--- 

**3.1 ArrayBlockingQueue**

- **배열 기반**의 고정 크기 큐입니다.
- 생성 시 용량(capacity)을 지정해야 하며, 이후 변경할 수 없습니다.
- **FIFO(First-In-First-Out)** 순서를 보장합니다.
- 선택적으로 **공정 모드(fairness)** 를 설정할 수 있으며, 공정 모드에서는 스레드들이 삽입·삭제를 요청한 순서대로 처리됩니다.
```java
// 공정 모드(fairness) 설정 예제
BlockingQueue<Integer> fairQueue = new ArrayBlockingQueue<>(10, true); // 공정 모드

// 비공정 모드(기본값) 설정 예제
BlockingQueue<Integer> unfairQueue = new ArrayBlockingQueue<>(10); // 비공정 모드(기본값)

```

---

**3.2 LinkedBlockingQueue**

- **연결 리스트 기반**의 큐이며, 기본적으로는 **무제한 크기**지만 생성자에서 용량을 제한할 수 있습니다.
- 특징적으로, **생산자(put)는 putLock**, **소비자(take)는 takeLock**을 사용합니다.
    - 즉, 큐가 가득 차 있지 않고 비어있지 않은 이상 **생산과 소비가 동시에 일어날 수 있어** ArrayBlockingQueue보다 처리량이 높습니다.
- 이러한 구조 덕분에 다중 스레드 환경에서 높은 처리 성능을 보입니다.
- 자바의 **스레드풀(ExecutorService)** 구현에서 기본 작업 큐로 자주 사용됩니다.

```java
// LinkedBlockingQueue 선언 예제
BlockingQueue<String> queue1 = new LinkedBlockingQueue<>(); // 기본: 무제한 크기
BlockingQueue<Integer> queue2 = new LinkedBlockingQueue<>(100); // 용량 제한: 100개
```

---

**3.3 PriorityBlockingQueue**

- 내부적으로 **힙 기반**의 자료구조를 사용합니다.
- 삽입 순서가 아니라 **우선순위(priority)** 에 따라 요소를 꺼냅니다.
- 크기는 사실상 **무제한**이며, 큐가 가득 차서 put()이 블로킹되는 일은 없습니다.
- 정렬 기준은 요소의 **자연 순서(Comparable)** 또는 생성 시 제공되는 Comparator에 따릅니다.
- 우선순위 작업 처리, 이벤트 스케줄링 등에 유용합니다.

---

**3.4 DelayQueue**

- 내부적으로 **PriorityBlockingQueue**를 기반으로 합니다.
- 요소는 반드시 **Delayed 인터페이스**를 구현해야 하며, 지정된 시간이 지나야 꺼낼 수 있습니다.
- **스케줄링, 타임아웃 처리, 캐시 만료 정책** 등을 구현할 때 자주 사용됩니다.

---

**3.5 SynchronousQueue**

- 내부 저장 공간이 없는, **용량 0**의 큐입니다.
- put()은 반드시 동시에 실행 중인 take()가 있어야만 성공합니다. → 이를 **핸드오프(Handoff) 방식**이라고 부릅니다.
- 즉, 데이터가 큐 안에 머무는 시간이 전혀 없으며, 곧바로 다른 스레드에 전달됩니다.
- 데이터를 쌓아두지 않고 **즉시 전달이 필요한 상황**(예: 작업을 생성 즉시 소비자 스레드에 전달해야 하는 스레드풀)에서 가장 효율적입니다.

---

**3.6 LinkedTransferQueue**

- **링크드 리스트 기반**의 무제한 큐입니다.
- SynchronousQueue의 **핸드오프 기능**과 LinkedBlockingQueue의 **비동기 버퍼링 기능**을 합친 고성능 큐입니다.
- transfer(e) : 소비자가 받을 때까지 생산자가 블로킹됩니다.
- tryTransfer(e) : 소비자가 대기 중일 때만 데이터를 전달하고, 그렇지 않으면 즉시 리턴합니다.
- 이처럼 **동기/비동기 전달을 상황에 따라 유연하게 선택**할 수 있다는 장점이 있습니다.
- 고성능 메시지 처리, 대규모 병렬 처리 환경에서 자주 사용됩니다.

---

**3.7 LinkedBlockingDeque**

- **양방향(Deque)** 을 지원하는 블로킹 큐입니다.
- 앞·뒤 양쪽에서 삽입(putFirst, putLast)과 삭제(takeFirst, takeLast)가 모두 가능합니다.
- **복잡한 작업 분산 패턴**에서 매우 유용합니다.
- 대표적인 활용 예시는 **작업 훔쳐오기(Work-Stealing) 패턴**입니다.
    - 각 스레드가 자신의 Deque에서 작업을 처리하다가, 할 일이 없으면 다른 스레드의 Deque 끝(Last)에서 작업을 훔쳐와(takeLast) 처리합니다.

---

**핸드오프란(handoff)?**
- 데이터를 큐에 쌓아두지 않고, 생산자 스레드에서 소비자 스레드로 즉시 전달하는 방식을 말한다.
- 즉 생산자가 put()을 하면, 이 데이터는 큐 안에 저장되는 것이 아니라 바로 소비자 스레드의 take()와 맞교환(hand-off)가 되어 전달된다.

---

SynchronousQueue로 핸드오프 구현 예제

- SynchronousQueue는 내부 저장 공간이 없어 생산자와 소비자가 동시에 만나야 데이터 전달이 가능합니다.  
- put() 호출 시 소비자가 take()를 호출할 때까지 블록됨  
- take() 호출 시 생산자가 put()를 호출할 때까지 블록됨  
- 직접적인 handoff 방식으로 동작하여 스레드 간 동기화된 데이터 전달 보장

```java
package thread.bounded;

import java.util.concurrent.SynchronousQueue;

public class BoundedQueueV7 implements BoundedQueue {

	private SynchronousQueue<String> queue;

	public BoundedQueueV7() {
		this.queue = new SynchronousQueue<>();
	}

	@Override
	public void put(String data) {
		try {
			queue.put(data); // 소비자가 나타날 때까지 대기
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public String take() {
		try {
			return queue.take(); // 생산자가 나타날 때까지 대기
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	@Override
	public String toString() {
		return queue.toString() + " (handoff)";
	}
}
```

```plain
22:31:43.913 [     main] == [생산자 먼저 실행] 시작, BoundedQueueV7 ==

22:31:43.915 [     main] 생산자 시작
22:31:43.924 [producer1] [생산 시도] data1 -> [] (handoff)
22:31:44.025 [producer2] [생산 시도] data2 -> [] (handoff)
22:31:44.130 [producer3] [생산 시도] data3 -> [] (handoff)

22:31:44.235 [     main] 현재 상태 출력, 큐 데이터: [] (handoff)
22:31:44.236 [     main] producer1: WAITING
22:31:44.236 [     main] producer2: WAITING
22:31:44.236 [     main] producer3: WAITING

22:31:44.236 [     main] 소비자 시작
22:31:44.237 [consumer1] [소비 시도]		? <- [] (handoff)
22:31:44.238 [consumer1] [소비 완료] data3 <- [] (handoff)
22:31:44.238 [producer3] [생산 완료] data3 -> [] (handoff)
22:31:44.339 [consumer2] [소비 시도]		? <- [] (handoff)
22:31:44.339 [consumer2] [소비 완료] data2 <- [] (handoff)
22:31:44.339 [producer2] [생산 완료] data2 -> [] (handoff)
22:31:44.442 [consumer3] [소비 시도]		? <- [] (handoff)
22:31:44.442 [consumer3] [소비 완료] data1 <- [] (handoff)
22:31:44.442 [producer1] [생산 완료] data1 -> [] (handoff)

22:31:44.544 [     main] 현재 상태 출력, 큐 데이터: [] (handoff)
22:31:44.544 [     main] producer1: TERMINATED
22:31:44.544 [     main] producer2: TERMINATED
22:31:44.544 [     main] producer3: TERMINATED
22:31:44.544 [     main] consumer1: TERMINATED
22:31:44.544 [     main] consumer2: TERMINATED
22:31:44.545 [     main] consumer3: TERMINATED
22:31:44.545 [     main] == [생산자 먼저 실행] 종료, BoundedQueueV7 ==
```
**1. 생산자 먼저 실행 단계:**

3개의 생산자가 순차적으로 put() 호출
모든 생산자가 WAITING 상태로 대기 (소비자가 없어서 블록됨)
큐는 계속 비어있음 [] (handoff) - 저장 공간이 없기 때문

**2. 소비자 실행 단계:**

소비자가 take() 호출하는 순간 대기 중인 생산자와 직접 매칭  
consumer1이 producer3과 매칭되어 data3 전달  
consumer2가 producer2와 매칭되어 data2 전달  
consumer3이 producer1과 매칭되어 data1 전달

**3. 핵심 특징:**

LIFO 순서: 마지막에 대기한 생산자(producer3)가 먼저 매칭됨  
직접 handoff: 중간 저장 없이 생산자→소비자 직접 전달  
동시 완료: 매칭된 순간 생산자와 소비자가 동시에 완료   

---

핸드오프는 언제?
- 실시간 전달이 중요한 경우
- 메모리 사용을 최소화해야하는 경우
- 백프레셔를 강하게 주고 싶을 때

뭔가.. 이번 축제 응모권 추첨을 좀 생각나게 하네요 ㅎㅎ