## 뮤텍스(Mutex)와 세마포어(Semaphore)의 차이



### 멀티스테링 동기화

1.  **멀티스레딩 환경**: 여러 스레드가 동시에 같은 리소스 접근 -> 동기화 문제
    - 일관성
     - 무결성
---
### 뮤텍스(Mutex)
1. **약자**: Mutual Exclusion(상호 배제)
2. **뜻**: 한 번에 하나의 스레드만 특정 리소스 접근하도록 제어하는 동기화 기법
3. **동작 순서**:
   - 잠그고(lock)
   - 작업을 수행
   - 해제(unlock)

4. **Java 구현**: ReentrantLock
```
private final Lock lock = new ReentrantLock();
lock.lock();

try{
    
} finally{
    lock.unlock();
}
// lock부터 unlock()까지 임계 영역
```
---
### 세마포어(Semaphore)
1. **어원**: 
- 고대 그리스어
  - 세마: 신호
  - 포어: 전달하는 것
- 운영체제 개념
  - 자원 접근을 제어하는 '신호' 역할
2. **뜻**: 특정 리소스에 동시에 접근할 수 있는 스레드의 수를 제한하는 동기화 기법
3. **동작 순서**:
- 허가증 획득(acquire())
- 허가증 반환(release())
4. **Java 구현**: Semaphore
```java
import java.util.concurrent.Semaphore;

public class SemaphoreExample {
    // 동시에 3개의 스레드만 접근 허용
    private final Semaphore semaphore = new Semaphore(3);
    private volatile int activeThreads = 0;

    /**
     * 세마포어를 이용한 제한된 리소스 접근
     * @param threadId 스레드 식별자
     */
    public void accessResource(int threadId) {
        try {
            // 자원 진입 시도
            System.out.println("🔄 Thread " + threadId + " is trying to access the resource.");

            // 허가증 획득 시도 (블로킹)
            semaphore.acquire();

            // 동시 접근 스레드 수 증가
            synchronized(this) {
                activeThreads++;
                System.out.println("✅ Thread " + threadId + " acquired permit. Active threads: " + activeThreads);
            }

            // 크리티컬 섹션 실행
            System.out.println("🎯 Thread " + threadId + " is accessing the resource.");

            // 작업 시뮬레이션
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ Thread " + threadId + " was interrupted");
        } finally {
            // 동시 접근 스레드 수 감소
            synchronized(this) {
                activeThreads--;
                System.out.println("🔓 Thread " + threadId + " is releasing permit. Active threads: " + activeThreads);
            }

            // 허가증 반환
            semaphore.release();
        }
    }
}
```
---
### 뮤텍스(Mutex) vs 세마포어(Semaphore)
| 구분          | 뮤텍스                                                              | 세마포어                                |
|-------------|------------------------------------------------------------------|-------------------------------------|
| 동시 접근 스레드 수 | 1(독점적)                                                           | N개(제한적)                             |
| 메커니즘        | Lock/Unlock                                                      | 	Acquire/Release                    |
| Java 구현체    | ReentrantLock                                                    | Semaphore                           |
| 사용 사례       | 1. 단일 리소스 보호 <br/>2. 데이터 무결성이 절대적으로 중요할 경우<br/>3. 순차적 처리가 필요한 작업 | 1. 제한된 리소스 풀 관리 <br/>2. 성능 최적화를 통한 병렬 처리 |
---
### 참고문헌:
https://notavoid.tistory.com/31

https://medium.com/@kwoncharles/%EB%AE%A4%ED%85%8D%EC%8A%A4-mutex-%EC%99%80-%EC%84%B8%EB%A7%88%ED%8F%AC%EC%96%B4-semaphore-%EC%9D%98-%EC%B0%A8%EC%9D%B4-de6078d3c453