# 3주차 발표자료: 동시성 자료구조 비교
### 목차
1. HashMap이란
2. ConcurrentHashMap이란 ?
   2-1. 일반 Hash map과의 차이
3. BlockingQueue와의 차이점 (용도와 목적 case별로 어떨 떈 뭐가좋고 )


## 1. HashMap이란
- Key-Value를 저장하는 자료구조
- 내부적으로 해시 함수를 사용하여 데이터를 배열 인덱스에 매핑
- 특징:
  - 순서 보장 X
  - Value 중복 O, Key 중복 X
  - 멀티 스레드 환경에서 put/get 동시 사용 시 데이터 꼬임 발생

---
그렇다면 ...
## 2. ConcurrentHashMap이란?
- 멀티 스레드 환경에서 안전하게 사용 가능하도록 설계된 HashMap 구현체
- 여러 스레드가 동시에 데이터를 넣고 조회하고 제거해도 데이터 꼬임 방지
- 동작 원리:
  - 단순히 Synchronized로 전체 Map을 잠그지 않음 (세그먼트 단위로 잠금 : 버킷 단위 락 ,예: 16개의 버킷을 4개의 세그먼트로 나누고, 세그먼트 단위로 락 걸기)
  - CAS 연산을 사용하여 필요한 부분만 잠금
  - 해시 충돌이 많을 경우 LinkedList 대신 Red-Black Tree로 변환하여 성능 보장

그렇다면 ..
### 2-1. 해시 충돌과 내부 구조
- 해시 충돌: 서로 다른 Key라도 해시값이 같아 동일한 배열 인덱스에 들어가는 상황
- HashMap 내부 구조:
  - 배열 각 칸에 Node 객체를 저장
  - 충돌 시 같은 버킷 내 Node를 연결 (LinkedList)
  - 버킷 내 Node가 많아지면 Tree 구조로 변경 (Red-Black Tree)
  - ConcurrentHashMap:
    - 버킷 단위(세그먼트)로 락을 걸어 동기화
    - 서로 다른 버킷은 별도 락 없이 동시 접근 가능

### 2-2. 일반 HashMap과 차이
- HashMap: 동기화 없음 → Multi-thread 환경에서 전체 lock 필요
- ![Waiting Threads.png](../../../../../Library/Containers/com.apple.Notes/Data/tmp/TemporaryItems/NSIRD_%EB%A9%94%EB%AA%A8_bVV9bJ/HardLinkURLTemp/9E46B47F-4DBB-41A3-8057-35F70EA8D5F9/1759392955/Waiting%20Threads.png)
- ConcurrentHashMap: Bucket 단위 동기화 → 성능 향상, 다른 버킷은 동시에 읽고 쓰기 가능
- ![Thread 1.png](../../../../../Library/Containers/com.apple.Notes/Data/tmp/TemporaryItems/NSIRD_%EB%A9%94%EB%AA%A8_bVV9bJ/HardLinkURLTemp/346731CA-3DEB-4241-816C-B439F1F79EEB/1759392976/Thread%201.png)
- 즉, 스레드 1가 bucket[0]에서 작업 중이라도
  스레드 2는 bucket[1]에서 자유롭게 읽고 쓰기 가능

---

## 3. BlockingQueue와의 차이
- 둘 다 동시성을 지원하지만, 목적과 용도가 다름

| 구분 | BlockingQueue | ConcurrentHashMap |
|------|---------------|------------------|
| 자료구조 형태 | 큐(Queue, FIFO) | 맵(Map, Key-Value) |
| 주요 목적 | 스레드 간 안전한 데이터 전달 (생산자-소비자 패턴) | 공유 데이터를 여러 스레드가 동시에 안전하게 읽고/쓰기 |
| 동작 특징 | - put() 시 꽉 차면 대기 (blocking)<br>- take() 시 비어있으면 대기 (blocking)<br>- 흐름 제어 기능 있음 | - 블로킹 없음 (대기 안 함)<br>- 키 단위 연산 원자적<br>- 빠른 읽기/쓰기 보장 |
| 사용 사례 | - 작업 큐 (스레드 풀)<br>- 로그 처리<br>- 이벤트/메시지 전달 | - 캐시 저장소 (세션, 로그인 정보)<br>- 실시간 카운팅/통계 집계<br>- 다중 스레드 공유 데이터 관리 |
| 순서 보장 | FIFO 순서 보장 | 순서 보장 없음 |
| 랜덤 접근 | 불가능 (순차 처리만 가능) | 가능 (Key 기반 접근) |
| Blocking 기능 | 있음 | 없음 |
| 단순 읽기 환경 | 비효율적 | 고성능 |
