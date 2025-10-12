## 섹션 10. 생산자 소비자 문제 2

### ReentrantLock
1.  **장점**: 스레드 대기 집합 분리
2. **메서드**:
   - newCondition(): 스레드 대기 공간 생성(여러 개 가능)
   - await(): 락 반납하고 대기
   - signal(): 지정한 condition에서 대기 중인 스레드 하나 깨움
---
### BlockingQueue
1.  **특징**: 
    - 자바에서 제공
     - 내부에서 ReentrantLock, condition, await, signal 사용
2. **구현체**:
    - ArrayBlockingQueue: 버퍼 크기 고정
    - LinkedBlockingQueue: 버퍼 크기 무한

3. **기능**:

| 구분      | Throws Exception | 대기 시 즉시 반환(false, null) | Blocks(대기) | 타임아웃                              |
|---------|------------------|-------------------------|------------|-----------------------------------|
| Insert  | add(e)           | offer(e)                | put(e)     | offer(e, time, unit)              |
| Remove  | remove()         | poll()                  | take()     | poll(time, unit)  |
| Examine | element()        | peek()                  | -          | - |

