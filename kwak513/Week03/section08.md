## 섹션 8. 고급 동기화 - concurrent.Lock

### synchronized 단점
1.  **무한 대기**: Blocked 상태의 스레드는 무한 대기, 인터럽트 X
2.  **공정성**: 어떤 스레드가 락을 획득할지 알 수 없음.

---

### LockSupport
1.  **park()**: 스레드를 WAITING 상태로
2.  **parkNanos(nanos)**: 스레드를 TIMED_WAITING 상태로
3.  **unpark(thread)**: 스레드를 RUNNABLE 상태로

---

### BLOCKED VS WAITING
1.  **BLOCKED**: 인터럽트 X, synchronized에서 대기 상태
2.  **WAITING**: 인터럽트 O
---

### Lock 인터페이스와 ReentrantLock: 무한 대기와 공정성 문제 해결
1.  **기능**:
    - **lock()**: 락 획득 시도, 만약 안되면 대기, 인터럽트 X (synchronized와 유사)
     - **lockInterruptibly()**: 락 획득 시도, 인터럽트 O
     - **tryLock()**: 즉시 성공 여부 반환
     - **tryLock(long time, TimeUnit unit)**: 특정 시간동안 락 획득 시도
     - **unlock()**: 락 해제


2. **공정 모드**: 
   - new ReentrantLock(true);


3. **공정 모드 vs 비공정 모드**:
   - **공정 모드**: 락 획득 순서 보장(공정성), 성능 저하
   - **비공정 모드**: 공정성 X, 성능 중시


4. **ReentrantLock 사용법**:
```
private final Lock lock = new ReentrantLock();
lock.lock();

try{
    
} finally{
    lock.unlock();
}
// lock부터 unlock()까지 임계 영역
```

