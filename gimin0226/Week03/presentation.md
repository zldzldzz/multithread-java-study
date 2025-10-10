# Java interrupt() 따라가기

## JNI(Java Native Interface)

“자바 코드  ↔ 네이티브 코드(C/C++ 등)”를 서로 호출·데이터 교환하게 해주는 표준 인터페이스

자바에서 직접 할 수 없는 OS/하드웨어 접근, 기존 C 라이브러리 재사용, 성능 임계 구간을 네이티브로 구현할 때 쓴다.

# 1. 클래스 로딩/초기화(`registerNative()` )

```java
//Thread.java 내 코드  
public class Thread implements Runnable {
    /* Make sure registerNatives is the first thing <clinit> does. */
    private static native void registerNatives();
    static {
        registerNatives();
    }
    
    ...
}
```

## 정적 초기화 블록

`static{ ... }` 는 정적 초기화 블록이다.

클래스가 최초로 로드되고 초기화될 때 단 한 번 실행되는 코드 덩어리다.

자바의 클래스는 JVM 안에서 로드(load) → 링크(link) → 초기화(initialize) 단계를 거친다.

- 로드(load) : 클래스 바이트코드를 메모리에 읽어옴
- 링크(link): 심볼 해결(메서드/필드 참조 확인)과 기본 메모리 배치
- 초기화(initialize): static 필드 초기화, `static {...}`  블록 실행

정적 초기화 블록은 클래스가 처음 사용되는 그 순간 딱 한 번만 실행된다.

`Thread` 클래스는 JVM 시작 직후부터 바로 사용되기 때문에, 부팅 과정에서 곧바로 초기화가 이뤄진다.

이 블록은 JVM 부팅 초반, `Thread` 클래스가 처음 로딩될 때 실행돼서  네이티브 메서드들을 전부 등록해버린다.

## Thread 클래스에서 왜 쓰나?

`Thread` 클래스는 내부에 네이티브 메서드(c 함수와 연결되는 `native` 키워드 메서드)가 많이 들어 있다.

이런 메서드는 자바 코드만으로는 실행할 수 없고, JVM 안의 C 구현과 연결되어야 한다.

그래서 `registerNatives()`라는 네이티브 메서드가 존재하는데, 이게 실제로는 JNI의 `RegisterNatives` 함수를 호출해서 

- 자바 메서드 이름(`start0`, `sleep`, `yield`, …)
- 자바 시그니처( ()V, (J)V, …)
- 실제 네이티브 구현 함수 포인터 (`JVM_StartThread`, `JVM_Sleep` , …)

를 매핑 테이블로 등록한다.

## `Make sure registerNatives is the first thing <clinit> does.`

- `<clinit>`은 클래스 초기화 메서드(컴파일러가 만들어주는 숨은 메서드)이다.
- 이 안에서 제일 먼저 해야 할 일이 바로 `registerNatives()` 호출이라는 의미
- 그렇지 않으면 나중에 `start0()`을 호출할 때 아직 C 함수와 연결이 안 되어 `UnsatisfiedLinkError` 가 날 수도 있기 때문이다.

# 2.`Java_java_lang_Thread_registerNatives` 실행

```c
//openjdk/src/java.base/share/native/libjava/Thread.c

static JNINativeMethod methods[] = {
    {"interrupt0",       "()V",        (void *)&JVM_Interrupt},
    ...
};

JNIEXPORT void JNICALL
Java_java_lang_Thread_registerNatives(JNIEnv *env, jclass cls) {
    (*env)->RegisterNatives(env, cls, methods,
        sizeof(methods)/sizeof(methods[0]));
}
```

## `JNINativeMethod`

`JNINativeMethod`는 JNI에서 제공하는 구조체이다.

각 원소는 자바 메서드와 C함수의 매핑 정보를 담는다.

`JNINativeMethod 구조체 정의`

```cpp
typedef struct {
	const char* name;     //자바 쪽 메서드 이름
	const char* signature;  //메서드 시그니처( ()V->인자 없음, 반환 void)
	void* fnPtr;          //네이티브 함수 포인터(실제로 호출할 C함수 포인터)
```

→ 즉, 자바의 `private native void interrupt0();`  와 C 함수 `JVM_Interrupt`를 연결하는 것이다.

## `Java_java_lang_Thread_registerNatives`

- JNI 네이밍 규칙(Java_패키지명_클래스명_메서드명)에 따라, `java.lang.Thread.registerNatives()` 와 연결된 네이티브 함수이다.
- JVM이 `Thread` 클래스를 초기화할 때 이 함수를 호출한다.
- `JNIEnv *env` : JVM이 제공하는 환경 포인터, JNI 함수들을 호출할 때 사용.
- `jclass cls` : 자바의 `class` 객체(`.class`)를 가리키는 JNI 타입

## `RegisterNatives` 호출

`(*env)->RegisterNatives(...)`는 JNI 함수로, 자바 클래스와 네이티브 함수들을 연결한다.

- methods[] 배열을 JVM에 등록하는 함수이다
- JVM의 메서드 테이블 안에 “자바 네이티브 메서드 호출 → C 함수 주소” 매핑이 들어가게 된다.
- 그 결과, 자바에서 `Thread.interrupt0()` 를 부를 때 JVM이 `methods` 배열을 보고 `JVM_Interrupt` 함수 포인터를 찾아 호출한다.

# 3. `Thread.interrupt()` 진입 → `interrupt0()` 호출

`Thread.interrupt()` 를 누르게 되면 `Thread` 클래스로 이동하게 된다.

```java
    public void interrupt() {
        if (this != Thread.currentThread()) {
            checkAccess();
        }

        // Setting the interrupt status must be done before reading nioBlocker.
        interrupted = true;
        interrupt0();  // inform VM of interrupt

        // thread may be blocked in an I/O operation
        if (this != Thread.currentThread()) {
            synchronized (interruptLock) {
                Interruptible b = nioBlocker;
                if (b != null) {
                    b.interrupt(this);
                }
            }
        }
    }
```

자바에서 interrupted 변수를 `true`로 바꾼 후 native 코드인 `interrupt0()` 을 호출한다.

```java
private native void interrupt0();
```

`interrupt0()` 메서드는 내용이 없고 `native`라는 키워드만 붙어 있었다.

- `native` 키워드: 이 메서드의 구현은 자바 코드가 아니라 네이티브 코드(C/C++ 등) 쪽에 있다. 즉, 선언만 자바에 있고, 실제 동작은 JVM이 JNI(Jana Native Interface)를 통해 운영체제나 C/C++ 라이브러리에 위임한다.

Thread.start()는 자바 코드지만, 실제 OS 인터럽트는 운영체제 API를 호출해야 한다.

이 OS 호출은 JVM 내부의 C++ 코드가 담당한다. 그래서 자바 쪽에는 `native`로만 선언해 두고, 내부에서 JVM의 네이티브 함수로 연결해 실행한다.

**요약: 자바는 “요청”을 하고, 실제 “상태 전이와 깨우기”는 JVM/OS가 한다.**

# 4. 네이티브 매핑 조회

JVM은 앞서 등록된 네이티브 테이블에서 `interrupt0` 의 구현 포인터를 찾아 `JVM_Interrupt`로 점프한다.

```java
static JNINativeMethod methods[] = {
    {"interrupt0",       "()V",        (void *)&JVM_Interrupt},
    ...
};
```

# 5. `JVM_Interrupt` → JVM 내부

```cpp
// HotSpot JVM의 네이티브 엔트리 포인트 매크로.
// - 현재 호출 스레드를 HotSpot 관점의 JavaThread* 로 셋업
// - (JNI/native → VM) 상태 전환과 예외 처리 프레임 준비
JVM_ENTRY(void, JVM_Interrupt(JNIEnv* env, jobject jthread))
  // 현재 호출 스레드(= 이 네이티브 함수를 실행 중인 스레드)가
  // 스레드 목록(ThreadsList)을 안전하게 볼 수 있도록 '스냅샷 핸들'을 잡는다.
  // 이 핸들이 살아있는 동안에는 대상 스레드(JavaThread*)가 리스트에서 사라지지 않도록 보장되어
  // 아래의 매핑 과정에서 생길 수 있는 레이스(종료/해제)를 방지한다.
  ThreadsListHandle tlh(thread);

  // 자바 레벨 Thread 객체(jobject jthread)에 대응하는 HotSpot의 JavaThread* 를 담을 포인터
  JavaThread* receiver = nullptr;

  // jthread(자바 Thread 객체)가 실제로 살아있는(= 아직 종료되지 않은) JavaThread 에
  // 연결되는지 확인하고, 연결되면 그 포인터를 receiver에 넣는다.
  // 반환값 is_alive == true 이면 jthread → JavaThread 매핑 성공 + 대상 스레드가 live.
  bool is_alive = tlh.cv_internal_thread_to_JavaThread(jthread, &receiver, nullptr);

  if (is_alive) {
    // 매핑이 성공했고 대상 스레드가 살아있다면 실제 인터럽트를 건다.
    // JavaThread::interrupt()는 HotSpot 내부 플래그 설정과 함께
    // SleepEvent/Parker/ParkEvent(= sleep/park/wait 대기)를 모두 깨워
    // 차단 상태에서 빠져나오게 트리거한다. (플랫폼별 부가 처리 포함)
    receiver->interrupt();
  }
JVM_END

```

## `jobject jthread`

- 자바 힙에 있는 `java.lang.Thread` 객체를 가리키는 핸들이다.

```cpp
Thread t = new Thread(...);
t.interrupt();
```

- 내부적으로 t 인스턴스(this)가 jthread로 넘어감

## `JavaThread 객체`

- JVM 내부(C++)에서 정의된 클래스
- 자바의 `Thread` 객체(힙에 있는 인스턴스)를 “실행 단위(OS 스레드)”와 연결해주는 네이티브 레벨 구조체
- 역할:
    - OS 스레드(OSThread)를 캡슐화
    - 자바 `Thread`객체와 매핑
    - GC·스케줄러·세이프포인트 같은 JVM 런타임 관리에 필요한 상태 보관
- 즉, 자바 측 Thread = 껍데기 핸들, C++ JavaThread = 실제 실행 엔진이다.

## `OSThread` vs `JavaThread` vs `자바의 Thread 객체`

- **OSThread**
    - 운영체제(OS)가 실제로 관리하는 커널 스레드를 감싸는 HotSpot C++ 클래스.
    - 내부에는 OS 핸들(pthread_t, Windows HANDLE 등)과 네이티브 스레드 관련 정보(스레드 ID, 상태, 신호 등)가 들어 있음.
    - 플랫폼 의존적.
- **JavaThread (C++ 클래스)**
    - JVM 레벨에서 스레드를 관리하는 C++ 객체.
    - 내부적으로 `OSThread* _osthread` 필드를 갖고 있어 OS 스레드와 연결됨.
    - GC, safepoint, 스케줄링, 자바 스택 관리, 모니터 락 추적 등 **JVM만의 관리 기능**을 추가로 제공.
    - 자바의 `Thread` 객체와도 연결됨 (`JavaThread::prepare(jthread)`에서 매핑).
- **자바의 `Thread` 객체 (힙 객체)**
    - 자바 코드에서 `new Thread(...).start()`로 만드는 객체.
    - 이름, 우선순위, daemon 여부, stackSize, `run()` 메서드 구현 등을 갖고 있음.
    - 네이티브 레벨의 `JavaThread`와 연결돼서 실행 단위를 제어할 수 있게 됨.

```scss
OS Thread (커널 스레드)
      ↑
   OSThread (HotSpot C++ 클래스)
      ↑
 JavaThread (HotSpot C++ 클래스, JVM 관리 기능 포함)
      ↑
java.lang.Thread (자바 힙 객체, jobject)

```

# 6. JVM 내부 → `JavaThread`

`receiver-> interrupt()` 로 `JavaThread` 내 `interrupt()` 메서드를 호출한다.

```cpp
 // 힙에서 ParkEvent 객체를 새로 만들어서 해당 스레드에 연결
 // 내부적으로 OS 이벤트 핸들을 초기화
 // 반환된 포인터를 _SleepEvent가 저장
 _SleepEvent(ParkEvent::Allocate(this)),

void JavaThread::interrupt() {
  // 모든 호출자는 반드시 이 스레드를 ThreadsListHandle로 보호해야 한다.
  // (스레드가 종료되거나 메모리에서 해제되는 동안 interrupt()가 호출되는 위험 방지)
  DEBUG_ONLY(check_for_dangling_thread_pointer(this);)

  // Windows 전용: OSThread의 interrupt 플래그를 true로 설정
  // (윈도우 구현에서는 이벤트 오브젝트로 인터럽트 상태를 관리)
  WINDOWS_ONLY(osthread()->set_interrupted(true);)

  // Thread.sleep()으로 잠든 스레드를 깨우기 위한 이벤트 신호
  // SleepEvent는 내부적으로 os::sleep에서 대기할 때 사용됨
  _SleepEvent->unpark();

  // JSR166 LockSupport.park() 상태에 있는 스레드를 깨우기
  // (java.util.concurrent의 park/unpark 메커니즘과 연결)
  parker()->unpark();

  // ObjectMonitor.wait() 또는 JVMTI RawMonitor.wait() 같은
  // 모니터 기반 대기(Object.wait, synchronized wait) 상태를 깨움
  _ParkEvent->unpark();
}

```

## `Thread.cpp` vs `JavaThread.cpp`

- `Thread` 는 모든 JVM 내부 스레드의 공통 베이스 클래스
- `JavaThread` 는 “자바 세계의 Thread”를 실행하는 스레드(= 자바 코드가 도는 스레드)용 서브클래스

```cpp
Thread                    // 공통 베이스(네이티브/JVM 측 스레드 껍데기)
├─ JavaThread             // 자바 코드(자바 프레임/스택) 실행 주체
└─ NonJavaThread          // GC, JIT, VMThread, WatcherThread 등 "자바 코드"를 안 도는 내부 스레드들
   ├─ VMThread / GCThread / CompilerThread / ServiceThread ...
   └─ (기타 여러 내부 스레드)

```

### 왜 `Thread.cpp`에는 `interrupt()` 가 없나?

1. 의미가 다름
    - 자바의 `Thread.interrupt()`는 `sleep()/wait()/park()` 같은 자바 런타임 대기 지점을 깨워 `InterruptedException`을 발생시키거나, 인터럽트 플래그를 세우는 자바 레벨 의미를 가진다.
    - 이 의미는 자바 코드를 실행하는 스레드(= `JavaThread`) 에만 적용된다. GC 스레드나 컴파일러 스레드 같은 내부 스레드(`NonJavaThread` 계열)는 자바 인터럽트 규약을 따르지 않는다.

### 요약

- `src/hotspot/share/runtime/thread.cpp`
    
    → `Thread`와 공통 로직 구현. 스레드 생성/종료, 네임, 디버깅 훅, 공통 유틸 등
    
- `src/hotspot/share/runtime/javaThread.cpp`
    
    → `JavaThread` 전용 로직 구현. 자바 프레임/스택, 자바 상태 전이, safepoint  협력, `JavaThread::interrupt()` 같은 자바 의미의 동작
    

## `JavaThread` 는 `_SleepEvent`, `parker`, `_ParkEvent` 를 한꺼번에 깨운다.

### 1. `_SleepEvent`

- 용도: `Thread.sleep()` 을 호출했을 때 쓰레드가 잠드는 이벤트 객체
- 내부적으로  `ParkEvent` 객체이고, `os::sleep`에서 해당 이벤트를 이용해 TIMED_WAITING 상태로 진입한다.
- 인터럽트가 발생하면 `_SleepEvent->unpark()` 로 깨워야 `sleep()`이 즉시 종료되고 `InterruptedException`을 던질 수 있다.

### 2. `parker`

- 용도: `LockSuppot.park()` 계열 API는 `Parker`라는 객체 안의 `_counter`와 플랫폼 이벤트를 써서 블로킹한다.

### 3. `ParkEvent`

- 용도: `Object.wait()`나 JVMTI RawMonitor.wait()는 `_ParkEvent->park()` 를 이용한다.
- 즉, `synchronized` 블록 안에서 `wait()` 호출했을 때 쓰레드를 블록시키는 이벤트 객체

### 왜 다 깨우나?

- 자바에서 `Thread.interrupt()`는 호출자 입장에서 이 쓰레드가 어떤 방식으로든 대기 중이면 바로 깨이나라라는 의미를 가진다.
- 스레드가 잠드는 방식
    - `Thread.sleep()`
    - `Object.wait()`
    - `LockSupport.park()`
    - (JVMTI 같은 내부 모니터 대기)
- 인터럽트를 걸 때 스레드가 어디에서 잠들어 있을지 JVM은 알 수 없기 때문에, 관련된 모든 대기 채널을 한꺼번에 unpark() 호출하는 것이다.

## `_SleepEvent` 와 `_ParkEvent` 는 같은 `ParkEvent` 타입

서로 다른 종류의 대기(waiting) 상태를 관리하기 때문에 분리해서 unpark() 한다.

### 왜 이렇게 분리해야 할까?

- **명확한 책임과 단순성**: `sleep`, `wait`, `park`는 대기 상태에 들어가는 이유와 깨어난 후의 처리 과정이 모두 다르다. 만약 하나의 `ParkEvent`를 공유한다면, 이 `unpark()` 신호가 `notify()`에 의한 것인지, `interrupt()`에 의한 것인지, 아니면 `sleep` 시간이 다 된 것인지 구분하는 로직이 매우 복잡해지고 버그가 발생하기 쉽다.
- **경쟁 상태 방지**: 만약 하나의 Parker를 공유한다면, `Object.wait()`을 깨우기 위한 `notify()` 신호가 우연히 `Thread.sleep()` 중인 스레드를 깨우는 등의 오작동을 일으킬 수 있다.
- **`interrupt()`의 역할**: `interrupt()` 메소드는 "이 스레드를 어떤 이유로든 대기 상태에 있다면 깨워라"라는 포괄적인 명령이다. JVM은 이 명령을 받았을 때 스레드가 현재 `sleep` 주차장에 있는지, `wait` 주차장에 있는지, `park` 주차장에 있는지 알 필요가 없다. 그냥 **모든 주차장에 "일어나!"라는 신호를 보내면,** 스레드가 주차된 곳에서만 신호가 유효하게 작용하는 것이다.

# 7. `JavaThread` → `_SleepEvent->unpark()`

`_SleepEvent->unpark()` 가 호출되면, `_SleepEvent` 포인터가 실제로 가리키는 객체의 `unpark()` 메서드가 호출된다. 

`_SleepEvent` 는 `ParkEvent` 타입이고, `ParkEvent`는 `PlatformEvent` 를 상속받아 OS별로  구현된 클래스다.

```cpp
 // 힙에서 ParkEvent 객체를 새로 만들어서 해당 스레드에 연결
 // 내부적으로 OS 이벤트 핸들을 초기화
 // 반환된 포인터를 _SleepEvent가 저장
 _SleepEvent(ParkEvent::Allocate(this)),
```

```cpp
class ParkEvent : public PlatformEvent {
  private:
    ParkEvent * FreeNext ;

    Thread * AssociatedWith ;

  private:
    static ParkEvent * volatile FreeList ;
    static volatile int ListLock ;

  protected:     
    ~ParkEvent() { guarantee (0, "invariant") ; }

    ParkEvent() : PlatformEvent() {
       AssociatedWith = nullptr ;
       FreeNext       = nullptr ;
    }

    void * operator new (size_t sz) throw();
    void operator delete (void * a) ;

  public:
    static ParkEvent * Allocate (Thread * t) ;
    static void Release (ParkEvent * e) ;
} ;

```

## `static ParkEvent * Allocate (Thread * t) ;`

이 함수는 특정 스레드 `t`가 대기 상태에 들어가기 전에 자신만의 `ParkEvent` 객체를 할당받는 과정을 담당한다.

`_SleepEvent`나 `_ParkEvent` 같은 `JavaThread`의 멤버 변수들은 이 `Allocate()`를 통해 초기화된다.

즉, 이 `ParkEvent` 객체는 이제부터 `t` 스레드 전용이다 라고 꼬리표를 붙이는 역할

## `static void Release (ParkEvent * e)`

- 스레드가 소멸하는 등 더 이상 `ParkEvent`가 필요 없을 때, 할당받았던 객체를 반납하는 역할을 한다.
- JVM은 `ParkEvent` 객체를 계속 새로 생성하고 삭제하는 대신, `FreeList`라는 풀에 사용하지 않는 객체들을 모아두고 재활용한다. `Relaese()`는 사용이 끝난 객체를 이 풀에 돌려놓는 함수이다.

# 8. `_SleepEvent-> unpark()` → `PlatformEvent::unpark()` (리눅스 기준)

```cpp
void PlatformEvent::unpark() {
  // _event 변수의 상태 전이 규칙:
  //    0 => 1 : 허가를 1로 세팅하고 종료.
  //    1 => 1 : 이미 허가가 있으므로 그대로 종료.
  //   -1 => 0 또는 1 : 스레드가 대기 중이므로 반드시 깨워야 함.
  //         안전하게 -1 상태를 0 또는 1로 바꿀 수 있음.
  // 참고: "Plan 9의 세마포어" 논문
  //
  // 참고: unpark() 시 -1에서 1로 상태를 바꾸면, 스레드는 park()를
  // 두 번 연속 호출해야 실제로 블로킹됨. 이는 unpark() 호출 후 첫 park()에서
  // 의도적인 "가짜 반환(spurious return)"을 유발하는 효과가 있음.
  // 이를 통해 상태 조건을 제대로 확인하지 않고 park/unpark를 사용하는 코드를
  // 걸러내는 데 도움이 됨. 이 가짜 반환은 사용자 코드에 나타나지 않으며,
  // ObjectMonitor, Mutex/Monitor, JavaThread::sleep 등에서 조건을
  // 올바르게 확인하는 루프 안에서만 나타남.

  // ---------------------------------------------------------------------------------
  // ⚡️ 1단계: 빠른 경로 (Fast-Path) - Lock-Free 최적화
  // ---------------------------------------------------------------------------------
  // AtomicAccess::xchg는 원자적으로(atomically) _event 변수의 값을 1로 설정하고,
  // 그 변수가 *이전에* 가지고 있던 값을 반환합니다.
  // 만약 이전 값이 0 또는 1이었다면 (>= 0), 스레드가 대기 중이 아니었다는 의미입니다.
  // 이 경우, _event 값을 1로 세팅(허가 부여)만 하고 아무도 깨울 필요가 없으므로 즉시 함수를 종료합니다.
  // 이 한 줄로 뮤텍스 락이나 비싼 OS 커널 호출 없이 대부분의 unpark 케이스를 처리할 수 있습니다.
  if (AtomicAccess::xchg(&_event, 1) >= 0) return;

  // ---------------------------------------------------------------------------------
  // 🐢 2단계: 느린 경로 (Slow-Path) - 스레드가 실제로 대기 중인 경우
  // ---------------------------------------------------------------------------------
  // 위의 if문을 통과했다는 것은 _event의 이전 값이 -1이었다는 의미입니다.
  // 즉, 스레드가 park()를 호출하고 현재 잠들어(대기하고) 있습니다.
  // 이제 이 스레드를 깨우기 위한 과정을 시작합니다.

  // _nParked 변수(실제로 대기 중인 스레드 수)를 안전하게 읽기 위해 뮤텍스를 잠급니다.
  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");

  // _nParked 값을 지역 변수 anyWaiters에 복사합니다.
  // 이 값은 0 또는 1이어야 합니다 (이벤트 객체 하나당 스레드 하나만 대기).
  int anyWaiters = _nParked;
  assert(anyWaiters == 0 || anyWaiters == 1, "invariant");

  // _nParked 확인이 끝났으므로 즉시 뮤텍스를 해제합니다.
  // 아래의 pthread_cond_signal()을 호출하기 전에 락을 푸는 것이 중요합니다.
  // 이는 불필요한 경쟁과 "헛된 깨우기(futile wakeup)" 현상을 줄여 성능을 향상시킵니다.
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");

  // ---------------------------------------------------------------------------------
  // 🔔 3단계: 실제 스레드 깨우기 (Wake-up)
  // ---------------------------------------------------------------------------------
  // anyWaiters가 0이 아니라면 (즉, 대기 중인 스레드가 있었다면),
  // pthread_cond_signal을 호출하여 _cond 조건 변수(condition variable)를
  // 기다리는 스레드에게 신호를 보냅니다.
  // 이 신호를 받은 스레드는 park() 메서드 내부의 pthread_cond_wait()에서 깨어나게 됩니다.
  if (anyWaiters != 0) {
    status = pthread_cond_signal(_cond);
    assert_status(status == 0, status, "cond_signal");
  }
}

```

---

### **1.  스레드가 정말 잠들어 있는지 판별하는 부분**

```cpp
if (AtomicAccess::xchg(&_event, 1) >= 0) return;
```

`interrupt`가 호출된 시점에 대상 스레드가 잠들어 있었다면(`_event` 상태가 -1), 이 `if` 문은 **실패**하고 아래의 깨우는 로직으로 넘어가게 된다. 만약 스레드가 깨어 있었다면(`_event`가 0 또는 1), 이 `if` 문이 성공해서 바로 `return` 해버린다.

---

### **2.  잠든 스레드를 실제로 깨우는 신호**

```cpp
if (anyWaiters != 0) {
    status = pthread_cond_signal(_cond);
}
```

- `pthread_cond_signal(_cond)`는 운영체제(OS) 커널에게 "**`_cond`라는 조건 변수를 기다리며 잠들어 있는 스레드를 지금 당장 깨워라**"라고 직접 명령하는 함수이다. `interrupt()`가 보낸 신호의 최종 목적지가 바로 여기이다.
이 호출을 통해 `sleep` 이나 `park` 등으로 멈춰 있던 스레드는 즉시 잠에서 깨어나 다음 동작(주로 `InterruptedException` 발생)을 준비하게 된다.

---

# 추가 내용

추가로 더 깊게 내려가지만 너무 깊어진다.

pthread_cond_signal(_cond)로 운영체제에게 스레드 깨우라고 명령한다는 점을 알면 된다.

더 궁금해 할 분들을 위해 함수 흐름을 남겨 놓겠다.

## 함수 흐름

interrupt() → `JavaThread::interrupt()` 가 `_SleepEvent/_ParkEvent/parker` 를 `unpark()` 

→ (sleep/wait 경로면) `PlatformEvent::unpark()` 가 `pthread_cond_singal()` 호출

→ glibc 내부에서 `futex_wake(...)` → `lll_futex_wake(...)`

→ `INTERNAL_SYSCALL(futex, 4, ...)` → `internal_syscall4(__NR_futex, ...)`

→ `asm volatile(”syscall”)` 실행 → 커널 `SYS_futex(FUTEX_WAKE| ...)` 진입

또한 `pthread_cond_signal()`에서 어떻게 깨우는지 궁금할 수 있으므로 `pthread_cond_signal()` 코드도 남겨 놓겠다.

## `glibc/nptl/pthread_cond_signal()`

```c
// pthread_cond_signal 함수의 내부 구현입니다.
// 인자로 깨울 스레드가 기다리는 조건 변수(cond)의 포인터를 받습니다.
___pthread_cond_signal (pthread_cond_t *cond)
{
  // 디버깅 및 프로파일링을 위한 매크로입니다.
  LIBC_PROBE (cond_signal, 1, cond);

  /* * 1. 대기 중인 스레드가 있는지 먼저 확인합니다.
   * __wrefs는 대기 중인 스레드의 참조 카운트와 비슷한 역할을 합니다.
   * atomic_load_relaxed는 가장 약한 메모리 순서 모델을 사용하는데,
   * 단순히 현재 대기자가 있는지 여부만 확인하는 것이므로 성능상 이점이 있습니다.
   * 정확한 숫자가 아니어도 '0인가 아닌가'만 판단하면 되기 때문입니다.
   * (wrefs >> 3)이 0이라는 것은 대기 중인 스레드가 없다는 의미입니다.
   */
  unsigned int wrefs = atomic_load_relaxed (&cond->__data.__wrefs);
  if (wrefs >> 3 == 0)
    return 0; // 대기 중인 스레드가 없으면 아무것도 안 하고 즉시 반환합니다.

  // 조건 변수가 프로세스 간 공유되는지(private=0) 여부를 확인합니다.
  int private = __condvar_get_private (wrefs);

  // 조건 변수의 내부 데이터 구조를 보호하기 위해 내부 lock을 획득합니다.
  // 사용자가 사용하는 뮤텍스와는 별개의 lock입니다.
  __condvar_acquire_lock (cond, private);

  /*
   * 2. 대기자 시퀀스 번호(wseq)를 읽어옵니다.
   * 이 값은 대기자들의 순서를 관리하는 데 사용됩니다.
   * glibc의 조건 변수는 두 개의 대기자 그룹(G1, G2)을 사용하는데,
   * wseq의 최하위 비트(LSB)는 현재 어떤 그룹이 활성 상태인지를 나타냅니다.
   */
  unsigned long long int wseq = __condvar_load_wseq_relaxed (cond);
  // g1은 현재 신호를 보낼 그룹의 인덱스(0 또는 1)를 결정합니다.
  // (wseq & 1) ^ 1 은 최하위 비트를 반전시켜, 현재 대기자들이 쌓이는 그룹이 아닌
  // 다른 그룹(신호를 받을 그룹)을 선택하게 합니다.
  unsigned int g1 = (wseq & 1) ^ 1;
  wseq >>= 1; // 시퀀스 번호 자체의 값을 얻기 위해 오른쪽으로 1비트 시프트합니다.

  // 실제로 커널에 스레드를 깨우라는 요청(futex_wake)을 보낼지 결정하는 플래그입니다.
  bool do_futex_wake = false;

  /*
   * 3. 신호를 보낼 그룹을 결정하고 신호를 추가합니다.
   * 이 부분이 이 구현의 핵심 로직입니다.
   *
   * 첫 번째 조건: cond->__data.__g_size[g1] != 0
   * 현재 신호를 받을 그룹(g1)에 대기 중인 스레드가 한 명이라도 있는 경우입니다.
   *
   * 두 번째 조건: __condvar_switch_g1 (cond, wseq, &g1, private)
   * 만약 g1 그룹이 비어있다면, 다른 그룹(G2)에 대기자가 있는지 확인하고
   * 두 그룹의 역할을 전환합니다. (G2 -> G1으로)
   * 성공적으로 전환되어 새로운 g1 그룹에 대기자가 생기면 이 조건이 참이 됩니다.
   */
  if ((cond->__data.__g_size[g1] != 0)
      || __condvar_switch_g1 (cond, wseq, &g1, private))
    {
      /*
       * 4. 선택된 그룹(g1)에 신호를 추가합니다.
       * __g_signals는 '깨어날 스레드의 수'를 의미하고, __g_size는 '대기 중인 스레드의 수'입니다.
       * atomic_fetch_add_relaxed를 사용해 신호 카운트를 1 증가시킵니다.
       * 이는 "이제 한 명 일어나도 좋다"는 신호를 보내는 것과 같습니다.
       */
      atomic_fetch_add_relaxed (cond->__data.__g_signals + g1, 1);
      // 대기자 수 카운트는 1 감소시킵니다. (곧 한 명이 깨어날 것이므로)
      cond->__data.__g_size[g1]--;
      // futex_wake를 호출해야 한다고 플래그를 설정합니다.
      do_futex_wake = true;
    }

  // 조건 변수의 내부 데이터 구조 변경이 끝났으므로 lock을 해제합니다.
  __condvar_release_lock (cond, private);

  /*
   * 5. 실제 스레드를 깨웁니다.
   * do_futex_wake 플래그가 설정된 경우에만 실행됩니다.
   */
  if (do_futex_wake)
    // futex_wake 시스템 콜을 호출하여 실제로 스레드를 깨웁니다.
    // 이 함수가 스레드의 상태를 변경하는 핵심입니다.
    futex_wake (cond->__data.__g_signals + g1, 1, private);

  // 성공적으로 함수를 종료합니다.
  return 0;
}
```

---

# 9. `pthread_cond_signal(_cond)` → 커널의 `futex(FUTEX_WAKE)` 시스템 콜 호출

## 커널 영역: 스레드를 깨움

1. **대기 스레드 검색**: 커널은 `_cond` 변수와 연결된 futex 주소에서 잠들어 있는(waiting) 스레드를 찾는다.
2. **스레드 상태 변경**: 커널은 해당 Target 스레드의 상태를 `TASK_INTERRUPTIBLE` (수면 상태) 에서 `TASK_RUNNING` (실행 가능 상태)으로 변경한다. 이는 스레드가 더 이상 잠들어 있지 않고, CPU를 할당받을 준비가 되었음을 의미한다.
3. **Run Queue에 추가**: 상태가 변경된 스레드는 CPU의 **실행 큐**에 다시 배치된다.
4. **스케줄링 및 실행**: 커널의 스레드가 다음 실행할 스레드로 이 Target 스레드를 선택하면, 스레드의 실행이 재개된다.

---

# 10. `futex(FUTEX_WAKE)` 시스템콜 → `pthread_cond_timedwait()` 복귀

스레드의 실행은 잠들기 직전, 즉 커널에 진입했던 바로 그 지점에서 재개된다.

1. 시스템 콜 반환: 스레드는 `pthread_cond_timedwait()` 함수 내부의 시스템 콜(`futex(FUTEX_WAIT)` ) 호출 지점으로 돌아온다. 즉, 커널 모드에서 사용자 모드로 복귀한다.
    - `pthread_cond_timedwait()` 함수를 호출하면, 스레드는 커널에 의해 잠들게 되고, 함수는 반환되지 않고 멈춰 있다.
2. **뮤텍스 재획득**: `pthread_cond_timedwait()`의 가장 중요한 동작 중 하나는 깨어난 직후, 잠들기 전에 풀어줬던 **뮤텍스(`_mutex`)를 다시 자동으로 잠그는 것**이다. 이는 스레드가 자신의 상태를 안전하게 확인하고 변경할 수 있도록 보장하는 핵심적인 안전장치이다.
    - 뮤텍스: 여러 스레드가 동시에 하나의 공유된 자원에 접근하는 것을 막기 위한 잠금 도구
    - 모니터락: 뮤텍스와 조건 변수를 포함하는 구조체, `synchronized` 블록에 진입하는 것은 뮤텍스를 얻는 것과 같고, 그 안에서 `wait()` 과 `notify()` 를 호출하는 것은 모니터의 조건 변수 기능을 사용하는 것이다.
3. `pthread_cond_timedwait()` 함수 반환: 뮤텍스를 성공적으로 잠근 후, 함수는 `0` (성공) 값을 반환하며 종료된다. 

# 11. `pthread_cond_timedwait()` → `os::sleep()` 복귀

이제 실행 흐름은 `glibc`를 호출했던 JVM의 C++ 코드로 돌아온다.

1. **`os::sleep()`으로 복귀**: `pthread_cond_timedwait()`를 호출했던 `os::sleep()` 함수로 제어권이 넘어온다. 이 시점에서 스레드는 여전히 **뮤텍스를 잠근 상태이**다.
2. **인터럽트 상태 확인**: `os::sleep()` 함수는 `PlatformEvent` 객체 내부의 상태 변수(`_event`)를 확인합니다. `unpark()`가 이 값을 `1`로 바꿔놓았기 때문에, 스레드는 자신이 **시간 만료가 아닌 인터럽트 때문에 깨어났다는 사실을 인지**한다.
3. **뮤텍스 해제**: 상태 확인을 마쳤으므로, 이제 **뮤텍스를 풀어준다(`pthread_mutex_unlock`).**
4. **인터럽트 상태 반환**: `os::sleep()` 함수는 "수면이 중단됨(interrupted)"을 의미하는 특정 값(예: `OS_INTRPT`)을 반환하며 종료한다.

# 12. `os::sleep()` → `JavaThread::sleep_nanos()` 복귀

```cpp
// Java 스레드를 지정된 나노초만큼 재우는 핵심 C++ 함수입니다.
// 인터럽트, 비동기 예외, Spurious Wakeup 등 다양한 엣지 케이스를 처리하도록 설계되었습니다.
// 성공적으로 잠을 마치면 true를, 인터럽트 등으로 중단되면 false를 리턴합니다.
bool JavaThread::sleep_nanos(jlong nanos) {
  // 1. 사전 조건 확인 (디버그 빌드에서만 동작):
  //    이 함수는 반드시 현재 실행 중인 스레드 자신에 대해서만 호출되어야 합니다.
  assert(this == Thread::current(),  "thread consistency check");
  //    시간은 음수일 수 없습니다. (상위 레벨인 JVM_Sleep에서 이미 체크했지만, 여기서도 확인합니다.)
  assert(nanos >= 0, "nanos are in range");

  // 2. Sleep 전용 ParkEvent 객체 가져오기:
  //    this->_SleepEvent는 이 스레드가 sleep을 위해 사용하는 전용 '주차 공간' 객체입니다.
  ParkEvent * const slp = this->_SleepEvent;

  // 3. ParkEvent 상태 초기화:
  //    만약 다른 스레드의 interrupt() 호출이 sleep() 호출보다 먼저 발생했다면,
  //    _SleepEvent 객체는 이미 'unparked' 상태일 수 있습니다.
  //    이 상태를 초기화(reset)하지 않으면, 아래의 park_nanos()가 잠들지 않고 즉시 리턴해버립니다.
  //    따라서 항상 깨끗한 상태에서 잠들기 위해 명시적으로 리셋합니다.
  slp->reset();

  // 4. 메모리 펜스(Memory Fence) 삽입:
  //    Java 메모리 모델(JMM)에서 interrupt() 호출은 happens-before 관계를 보장해야 합니다.
  //    이 펜스는 컴파일러나 CPU가 코드 순서를 임의로 변경하는 것을 막아줍니다.
  //    이를 통해 다른 스레드에서 변경된 'interrupted' 플래그 값을 이 스레드에서 확실하게
  //    볼 수 있도록 보장합니다.
  OrderAccess::fence();

  // 5. 시간 추적 시작:
  //    os::javaTimeNanos()는 시스템 시간 변경에 영향을 받지 않는 단조 증가(monotonic) 시간 소스를 사용합니다.
  //    이를 통해 정확한 대기 시간을 계산할 수 있습니다.
  jlong prevtime = os::javaTimeNanos();

  // 남은 대기 시간을 저장할 변수입니다.
  jlong nanos_remaining = nanos;

  // 6. 메인 루프 진입:
  //    Spurious Wakeup(허위 пробуждение)이나 인터럽트로 인해 일찍 깨어났을 경우,
  //    남은 시간만큼 다시 잠들게 하기 위해 무한 루프를 사용합니다.
  for (;;) {
    // 6a. 비동기 예외 확인:
    //     Thread.stop() 같은 (deprecated된) 외부 요인에 의한 예외가 있는지 먼저 확인합니다.
    if (has_async_exception_condition()) {
      return false; // 예외가 있으면 즉시 중단
    }

    **// 6b. 인터럽트 상태 확인 (루프의 가장 중요한 부분):
    //     잠들기 전, 그리고 잠에서 깨어난 후(루프가 다시 돌 때) 항상 인터럽트 상태를 확인합니다.
    //     인터럽트가 시간 만료보다 우선순위가 높습니다.
    //      is_interrupted(true)는 상태 확인과 동시에 플래그를 초기화합니다.
    if (this->is_interrupted(true)) {
      return false; // 인터럽트가 걸렸으면 중단
    }**

    // 6c. 시간 만료 확인:
    //     남은 시간이 없다면 성공적으로 잠을 마친 것입니다.
    if (nanos_remaining <= 0) {
      return true; // 정상 종료
    }

    // 6d. 실제 스레드 대기 (Blocking):
    //     이 중괄호 بلوك 안에서 실제 스레드가 잠들게 됩니다.
    {
      // ThreadBlockInVM: 이 객체가 생성되는 동안, 이 스레드는 VM 내에서 블록된 상태임을
      //                  GC(Garbage Collector) 등 다른 VM 시스템에게 알립니다. (RAII 패턴)
      ThreadBlockInVM tbivm(this);
      // OSThreadWaitState: jstack 등에서 스레드 상태를 'SLEEPING'으로 보여주도록 설정합니다. (RAII 패턴)
      OSThreadWaitState osts(this->osthread(), false /* not Object.wait() */);
      
      // ParkEvent를 사용하여 지정된 나노초만큼 스레드를 대기시킵니다.
      // 이 함수가 내부적으로 pthread_cond_timedwait (Linux) 또는 WaitForSingleObject (Windows)를 호출합니다.
      slp->park_nanos(nanos_remaining);
    }

    // 7. 깨어난 후 남은 시간 재계산:
    //    여기까지 코드가 도달했다는 것은 park_nanos에서 깨어났다는 의미입니다.
    //    (이유: 정상적인 시간 만료, 인터럽트, 또는 Spurious Wakeup)
    jlong newtime = os::javaTimeNanos();
    if (newtime - prevtime < 0) {
      // 드물게 시스템 시간이 거꾸로 가는 경우에 대한 방어 코드입니다.
      assert(false,
             "unexpected time moving backwards detected in JavaThread::sleep()");
    } else {
      // 실제로 잠들어 있던 시간을 계산하여 남은 대기 시간에서 빼줍니다.
      nanos_remaining -= (newtime - prevtime);
    }
    // 다음 루프를 위해 현재 시간을 기록합니다.
    prevtime = newtime;
  }
}
```

## 동작 흐름

1. **첫 번째 루프 진입 (잠들기 전)**
    - `for (;;)` 루프가 시작된다.
    - `this->is_interrupted(true)`를 검사한다. 아직 인터럽트가 없으므로 `false`이다.
    - `nanos_remaining`도 아직 0보다 크므로 통과한다.
    - 마침내 `slp->park_nanos(...)`를 호출하여 스레드가 **잠에 든다.**
2. **인터럽트 발생 및 깨어남**
    - 다른 스레드가 `interrupt()`를 호출한다.
    - `slp->park_nanos()` 내부에서 대기하던 스레드는 **강제로 깨어난다.**
    - `slp->park_nanos()` 함수가 반환되고, 실행 흐름은 그 다음 줄부터 계속된다.
3. **깨어난 직후**
    - 코드는 남은 시간을 재계산하는 로직을 실행한다.
    - `for (;;)` 루프의 끝에 도달했으므로, 다시 **루프의 맨 처음으로 돌아간다.**
4. **두 번째 루프 진입 (깨어난 후)**
    - `for (;;)` 루프가 다시 시작한다.
    - **바로 이 시점**에, 아까는 지나쳤던 `this->is_interrupted(true)`를 **다시 검사**하게 된다.
    - 이번에는 인터럽트 때문에 깨어났기 때문에, 이 함수는 `true`를 반환한다.
    - `if` 조건이 참이 되어, 함수는 `return false;`를 실행하며 `sleep`을 즉시 종료한다.

## 12-1. `JavaThread::is_interrupted(bool)` 로 인터셉트 상태 확인

```cpp
// 스레드의 인터럽트 상태를 확인하고, 필요에 따라 그 상태를 초기화(clear)하는 C++ 구현체입니다.
// java.lang.Thread.isInterrupted() (clear_interrupted=false)와
// java.lang.Thread.interrupted() (clear_interrupted=true) 네이티브 메서드의 기반이 됩니다.
bool JavaThread::is_interrupted(bool clear_interrupted) {
  // 1. 포인터 유효성 검사 (디버그 빌드에서만 동작):
  //    이 함수를 호출하는 'this' JavaThread 포인터가 유효한지(dangling pointer가 아닌지) 확인합니다.
  DEBUG_ONLY(check_for_dangling_thread_pointer(this);)

  // 2. Java 미러(Mirror) 객체 존재 여부 확인:
  //    _threadObj는 이 C++ JavaThread 객체에 해당하는 Java 세계의 java.lang.Thread 객체에 대한 참조입니다.
  //    이 참조가 null이면(아직 Java 객체와 연결되지 않았으면) 인터럽트 플래그를 저장할 곳이 없으므로
  //    인터럽트될 수 없습니다. (JVM 초기화, JNI 스레드 attach 과정 등에서 발생 가능)
  if (_threadObj.peek() == nullptr) {
    assert(this == Thread::current(), "invariant");
    return false;
  }

  // 3. Java 수준의 인터럽트 플래그 읽기:
  //    java_lang_Thread::interrupted는 C++ 헬퍼 함수로, threadObj()가 가리키는
  //    java.lang.Thread 객체 내부의 'private volatile boolean interrupted' 필드 값을 읽어옵니다.
  bool interrupted = java_lang_Thread::interrupted(threadObj());

  // 4. [중요] 인터럽트 처리의 미묘한 경쟁 상태(Race Condition)에 대한 노트:
  //    - 이 작업은 별도의 lock 없이 수행됩니다. 즉, 인터럽트 플래그를 읽는 동작과
  //      unpark()로 이벤트를 깨우는 동작이 원자적(atomic)으로 묶여있지 않습니다.
  //
  //    - 이로 인해, 플래그는 'false'인데 하위 레벨의 이벤트(Parker/ParkEvent)는
  //      'signaled' 상태일 수 있는 아주 드문 상황이 발생할 수 있습니다. 예를 들어,
  //      A 스레드가 B를 interrupt() -> B의 플래그가 true가 되고 이벤트가 unpark됨
  //      C 스레드가 B의 is_interrupted(true) 호출 -> B의 플래그가 false로 바뀜
  //      B 스레드가 park() 진입 -> 이미 unpark된 이벤트 때문에 즉시 깨어남
  //
  //    - 이 현상은 의도된 설계입니다. 이런 스레드는 Object.wait()이나 LockSupport.park()에서
  //      즉시 깨어나게 되는데, 이는 'Spurious Wakeup(허위 пробуждение)'으로 간주됩니다.
  //      Java 명세는 Spurious Wakeup을 허용하므로 해롭지 않습니다.
  //
  //    - 이 드문 경우를 막기 위해 또 다른 lock을 추가하는 것은 복잡성과 성능 저하를
  //      야기하므로 가치가 없다고 판단되었습니다.
  //
  //    - 또한 lock이 없기 때문에, 우리는 '인터럽트가 걸렸다고 보고할 경우에만'
  //      인터럽트 상태를 초기화해야 합니다. 그렇지 않으면, 플래그를 읽은 직후에
  //      발생한 다른 인터럽트 신호가 유실될 수 있습니다. (아래 if문의 조건이 바로 그 이유입니다)
  if (interrupted && clear_interrupted) {
    // 5. 인터럽트 상태 초기화(clear) 로직:
    //    Java 명세에 따라, 오직 현재 실행 중인 스레드만이 자신의 인터럽트 상태를
    //    초기화할 수 있습니다 (java.lang.Thread.interrupted()의 동작 방식).
    assert(this == Thread::current(), "only the current thread can clear");

    **//    실제 java.lang.Thread 객체의 'interrupted' 필드에 false를 씁니다.
    java_lang_Thread::set_interrupted(threadObj(), false);

    //    Windows의 경우, 이전에 설명했던 네이티브 OSThread 수준의 인터럽트 플래그도 함께 초기화해줍니다.
    //    이를 통해 Java 수준의 플래그와 네이티브 수준의 플래그 상태를 일치시킵니다.
    WINDOWS_ONLY(osthread)->set_interrupted(false);)
  }**
  **// 6. 초기화하기 '전'의 인터럽트 상태를 리턴합니다.
  //    이를 통해 호출자는 인터럽트가 발생했었는지 여부를 알 수 있습니다.
  return interrupted;**
}
```

### 1. 인터럽트 상태 확인

```cpp
bool interrupted = java_lang_Thread::interrupted(threadObj());
```

- 이 코드는 c++ JVM 에서 Java 힙에 있는 `java.lang.Thread` 객체 내부의 `private volatile boolean interrupted` 필드 값을 직접 읽어오는 부분이다.
- `_threadObj`는 **C++ `JavaThread` 객체 안에 있는 멤버 변수**로, 자신의 짝이 되는 **Java `java.lang.Thread` 객체를 가리키는 참조(reference)**이다. 즉, C++ 세계와 Java 세계를 연결하는 다리 역할을 한다.
    - 하지만 이 참조는 단순한 C++ 포인터가 아니다. Java 객체는 **가비지 컬렉터(GC)**에 의해 메모리 주소가 언제든지 바뀔 수 있기 때문이다. 만약 C++ 코드가 Java 객체의 실제 주소를 그대로 들고 있다면, GC가 일어난 후에 그 포인터는 쓸모없는 값이 되어버린다.
    - 이를 해결하기 위해 JVM은 **핸들(Handle)** 또는 `oop`(Ordinary Object Pointer)라는 개념을 사용한다. 이는 GC가 관리하는 간접적인 참조로, GC가 객체를 옮기더라도 핸들 값은 그대로 유지되고 JVM이 내부적으로 실제 주소를 찾아준다. `_threadObj`는 바로 이런 종류의 안전한 핸들이다.

### 2. 조건부 초기화

```cpp
if (interrupted && clear_interrupted) {
    // ...
    java_lang_Thread::set_interrupted(threadObj(), false);
    // ...
}
```

- **설명**: 이 코드는 인터럽트 상태를 초기화(clear)할지 말지를 결정하는 핵심 로직이다. 플래그를 초기화하는 조건은 **두 가지 모두** 충족되어야 한다.
    1. `interrupted`: 실제로 인터럽트가 걸려있는 상태여야 한다.
    2. `clear_interrupted`: 호출자가 초기화를 요청했다 (즉, `Thread.interrupted()`가 호출되었다).
    
     `java.lang.Thread.isInterrupted()` (clear_interrupted=false)
     `java.lang.Thread.interrupted()` (clear_interrupted=true)
    
- **중요성**: 이 로직은 `Thread.isInterrupted()`(읽기만 함)와 `Thread.interrupted()`(읽고 초기화함)의 동작을 구분하는 핵심이다.

# 13. `JavaThread::sleep_nanos()` → `JVM_SleepNanos()` 복귀

```cpp
// JNI 네이티브 메서드인 java.lang.Thread.sleep(long millis, int nanos)의 JVM 내부 구현체입니다.
// JVM_ENTRY 매크로는 JNI 호출을 위한 기본적인 스레드 상태 설정과 예외 처리 스코프를 자동으로 관리해 줍니다.
JVM_ENTRY(void, JVM_SleepNanos(JNIEnv* env, jclass threadClass, jlong nanos))

  // 1. 유효성 검사: 나노초가 음수이면 Java 명세에 따라 예외를 던집니다.
  if (nanos < 0) {
    THROW_MSG(vmSymbols::java_lang_IllegalArgumentException(), "nanosecond timeout value out of range");
  }

  // 2. 사전 인터럽트 확인: 잠들기 *전에* 이미 스레드가 인터럽트된 상태인지 확인합니다.
  //    이는 interrupt()가 sleep()보다 먼저 호출된 엣지 케이스를 처리하기 위함입니다.
  //    is_interrupted(true)는 확인과 동시에 인터럽트 상태를 초기화(clear)합니다.
  //    만약 다른 예외가 이미 발생 대기 중이라면(HAS_PENDING_EXCEPTION), 그 예외를 덮어쓰지 않도록 합니다.
  if (thread->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
    THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
  }

  // 3. 스레드 상태 관리 (RAII 패턴):
  //    JavaThreadSleepState 객체가 생성될 때 현재 Java 스레드의 상태를 _thread_blocked로 변경하고,
  //    OS 스레드의 상태를 SLEEPING으로 설정합니다.
  //    이 함수가 어떤 경로로든(정상 종료, 예외 발생 등) 종료되면, jtss 객체의 소멸자가 호출되어
  //    원래 스레드 상태로 '자동으로' 복원해 줍니다. 매우 안전하고 편리한 방식입니다.
  JavaThreadSleepState jtss(thread);

  // 4. JFR/DTrace 프로브: Java Flight Recorder 같은 모니터링 도구에게
  //    "지금부터 스레드가 sleep을 시작한다"고 알려주는 신호입니다. 디버깅 및 프로파일링에 사용됩니다.
  HOTSPOT_THREAD_SLEEP_BEGIN(nanos / NANOSECS_PER_MILLISEC);

  // 5. nanos == 0 특별 처리: sleep(0)은 OS 스케줄러에게 현재 스레드의 CPU 사용 시간을
  //    다른 스레드에게 양보하라는 힌트입니다. os::naked_yield()가 이 역할을 합니다.
  if (nanos == 0) {
    os::naked_yield();
  } else {
    // 6. 실제 스레드 대기 로직 (nanos > 0):
    ThreadState old_state = thread->osthread()->get_state();
    thread->osthread()->set_state(SLEEPING);

    // 7. 핵심 호출: 이전 단계에서 자세히 분석했던 바로 그 함수를 호출합니다.
    //    이 함수는 정상적으로 시간이 만료되면 true를,
    //    인터럽트되거나 다른 비동기 예외가 발생하면 false를 리턴합니다.
    if (!thread->sleep_nanos(nanos)) { // interrupted or async exception was installed
      // 8. 인터럽트 또는 비동기 예외 처리:
      //    sleep_nanos가 false를 리턴했으므로, 인터럽트 또는 비동기 예외 상황입니다.
      //    다른 비동기 예외(예: deprecated된 Thread.stop)가 이미 설정되어 있다면,
      //    InterruptedException으로 덮어쓰지 않도록 먼저 확인합니다.
      if (!HAS_PENDING_EXCEPTION) {
        // JFR 프로브: 스레드 sleep이 '중단(interrupted)'되어 끝났음을 알립니다 (인자 1).
        HOTSPOT_THREAD_SLEEP_END(1);
        // 비동기 예외가 없는 것을 다시 한번 확인하고,
        if (!thread->has_async_exception_condition()) {
          // 최종적으로 InterruptedException 예외 객체를 생성하고 현재 스레드에 던지도록 설정합니다.
          // 이 THROW_MSG가 호출되면 이 함수는 여기서 즉시 종료됩니다.
          THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
        }
      }
    }
    // (주: 이 코드는 jtss RAII 객체에 의해 사실상 자동으로 처리되지만, 명시적으로 상태를 관리하는 부분도 남아있습니다.)
    thread->osthread()->set_state(old_state);
  }
  // JFR 프로브: 스레드 sleep이 '정상 종료'되었음을 알립니다 (인자 0).
  HOTSPOT_THREAD_SLEEP_END(0);

```

## 중요 코드 분석

### 1. 사전 인터럽트 체크

```cpp
if (thread->is_interrupted(true) && !HAS_PENDING_EXCEPTION) {
    THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
  }
```

- `sleep` 에 들어가기 직전 인터럽트 상태를 검사하고, `true` 면  즉시 예외를 던진다.
- `thread`는 `JavaThread*` 를 의미
- `JavaThread` 의 is_interrupted() 메서드를 호출함
    - `true` 인수는 읽으면서 플래그를 지움을 의미한다.
- 요약: sleep가 들어오기 전에 이미 인터럽트가 있었는지 체크하고 있었다면 플래그를 지우고 즉시 `InterruptedException` 예외를 던진다.

### 2. `THROW_MSG` 로 예외 날림

```cpp
if (!thread->sleep_nanos(nanos)) { 
      if (!HAS_PENDING_EXCEPTION) {
        HOTSPOT_THREAD_SLEEP_END(1);
        if (!thread->has_async_exception_condition()) {
          THROW_MSG(vmSymbols::java_lang_InterruptedException(), "sleep interrupted");
        }
      }
    }
```

- `JavaThread` 의 `sleep_nanos()`를 호출하고 리턴된 값으로 반복문 여부를 정함
    - true: 시간이 만료되서 리턴됨 (시간 다 채워서 정상 타임아웃)
    - false: 인터럽트/비동기 예외로 리턴됨
- `sleep_nanos` 가 인터럽트로 깼을 때 자바 레벨 규약대로 `InterruptedException` 을 던지게 변환한다.

# 14. `THROW_MSG()` 이후

1.  **예외 객체 생성 및 등록**
    - `THROW_MSG` 매크로는 먼저 Java 힙에 `java.lang.InterruptedException` 객체를 생성한다.
    - 그리고 현재 실행 중인 `JavaThread` 객체에 "처리해야 할 예외가 있음(Pending Exception)"이라고 이 예외 객체를 **등록한다.**
    - 그 후, 실행 중이던 `JVM_Sleep` 은 즉시 종료된다.
2. **JVM의 상태 확인**
    - `Thread.sleep()`이라는 네이티브 메소드 호출이 끝났으므로, 제어권은 잠시 JVM으로 돌아온다.
    - JVM은 네이티브 메소드가 반환될 때마다, 항상 "혹시 처리해야 할 예외가 등록되었나?"를 확인한다.
    - JVM은 `JavaThread`에 `InterruptedException`이 등록된 것을 발견한다.
3. **예외 던지기 및 스택 역추적** 
    - JVM은 등록된 예외 객체를 가져와, `Thread.sleep()`을 호출했던 **바로 그 Java 코드 라인에서 예외를 던진다(throw).**
    - JVM은 현재 메소드의 호출 스택을 거슬러 올라가면서, 이 `InterruptedException`을 처리할 수 있는 가장 가까운 `try-catch` 블록을 찾기 시작한다.
- **최종 목적지: `catch` 블록**
    - 마침내 `catch (InterruptedException e)` 블록을 찾으면, JVM은 실행 흐름을 그곳으로 옮긴다.
    - C++에서 생성되었던 예외 객체는 `e` 변수에 담겨 Java 코드에서 사용할 수 있게 된다.

# 15. 핵심 요약

1. **`interrupt()`의 두 가지 핵심 동작**
    - **플래그 설정:** `private volatile boolean interrupted` 필드를 `true`로 설정한다. (`volatile` 키워드가 다른 스레드에게 즉각적인 가시성을 보장한다.)
    - **깨우기 신호 전송:** `interrupt0()` 네이티브 메서드를 통해 JVM 내부 함수(`JVM_Interrupt`)를 호출하여, 스레드가 어떤 이유로든(`sleep`, `wait`, `park`) 대기 중이라면 즉시 깨우는 신호(`unpark`)를 보낸다.
2. **JNI: 자바와 네이티브 세계의 연결고리**
- `Thread` 클래스가 JVM에 로드될 때, `registerNatives()`가 가장 먼저 호출되어 자바의 `interrupt0()` 메서드와 C++로 구현된 `JVM_Interrupt` 함수를 **미리 연결**해 둔다. 이 덕분에 자바 코드에서 네이티브 코드를 원활하게 호출할 수 있다.
1. **객체의 이중성: Java `Thread` vs C++ `JavaThread`**
    - 우리가 다루는 자바의 `Thread` 객체는 일종의 **'리모컨' 또는 '핸들'**에 가깝다. 실제 스레드의 상태, 스택, OS 스레드 정보 등은 JVM 내부의 C++ 객체인 **`JavaThread`**가 관리한다. `interrupt()`의 핵심 로직은 바로 이 `JavaThread` 객체에서 수행된다.
2. **JVM의 역할: 모든 대기 채널 깨우기**
    - `JavaThread::interrupt()` 함수는 `_SleepEvent`(`Thread.sleep`용), `parker`(`LockSupport.park`용), `_ParkEvent`(`Object.wait`용) 세 가지 대기 채널에 모두 `unpark()` 신호를 보낸다. 이는 스레드가 어디서 잠들어 있든 확실하게 깨우기 위함이다.
3. **OS의 역할: 실제 스레드 상태 변경**
    - `unpark()` 신호는 최종적으로 리눅스의 `pthread_cond_signal()`을 통해 커널의 `futex(FUTEX_WAKE)` 시스템 콜을 호출한다.
    - 커널은 대상 스레드의 상태를 **`TASK_INTERRUPTIBLE`(수면)에서 `TASK_RUNNING`(실행 가능)**으로 바꾸고 CPU 실행 큐에 다시 넣는다.
4. **깨어난 후의 처리: 인터럽트 상태 확인 및 예외 변환**
    - 잠에서 깨어난 스레드는 `sleep_nanos()`의 `for(;;)` 루프 안에서 자신의 `is_interrupted()` 상태를 확인한다.
    - 인터럽트 플래그가 `true`인 것을 확인하면, `sleep_nanos`는 `false`를 반환한다.
    - 상위 호출자인 `JVM_Sleep`은 `false` 반환 값을 보고, 현재 스레드에 **`InterruptedException` 예외를 생성하여 던진다(throw).** 이 예외가 바로 우리가 `try-catch` 블록에서 잡는 그 예외이다.

---

# 16. 배운점

1. **Java는 OS 위에 세워진 정교한 성채다**
    - `Thread.sleep()`이라는 간단한 메서드 하나가 실제로는 **JNI → JVM(C++) → OS 커널**을 거치는 것을 알게 되었다. 자바가 얼마나 높은 수준의 추상화를 제공하는지, 그리고 그 이면에서 얼마나 복잡한 작업이 일어나는지 체감할 수 있었다.
2. **`interrupt()`는 '강제 종료'가 아닌 '친절한 알림'이다**
    - `interrupt()`는 스레드를 파괴하는 신호가 아니라, "혹시 자고 있다면 일어나서 이 플래그(interrupted status) 한번 확인해 줄래?"라고 요청하는 **협력적인 메커니즘**이라는 점을 명확히 이해했다. 실제 스레드를 멈출지 말지는 전적으로 깨어난 스레드의 로직에 달려있다.
3. **JVM은 스레드의 '만능 관리인'이다**
    - 스레드가 `sleep()`, `wait()`, `park()` 등 다양한 이유로 멈출 수 있는데, JVM은 이 모든 대기 상태(`_SleepEvent`, `_ParkEvent`, `parker`)를 별도로 관리하고 있었다. `interrupt()`가 호출되면 JVM은 스레드가 어디서 자고 있는지 신경 쓰지 않고 **모든 곳에 "일어나"라고 소리쳐 깨우는** 방식이 매우 인상적이었다.
4. **Java 예외는 JVM이 만드는 것이다.**
    - OS 커널이 보낸 `futex_wake`라는 원초적인 깨우기 신호가 C++ 코드를 거치면서 `false`라는 boolean 값으로 변환되고, 최종적으로 JVM에 의해 `InterruptedException`이라는 Java 예외 객체로 포장되어 던져지는 과정은 정말 정교한 설계의 결과물이었다.
