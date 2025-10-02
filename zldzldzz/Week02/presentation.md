## 1. `ThreadLocal`: 왜 굳이  씁니까?

- `ThreadLocal`은 메서드 호출을 넘어, **요청 단위로 데이터를 안전하게 공유**하기 위해 사용합니다.

**ThreadLocal<T> = “스레드 전용 사물함”**

같은 ThreadLocal<T>여도**스레드마다 서로 다른 값**을 보관한다.

단, **자료형 T는 고정**

(예: `ThreadLocal<String>` 이면 항상 String만)

## 2. 왜 `ThreadLocal`이 필요한가요?

- 지역변수 = **손에 든 메모**.
    - 그 **메서드 안에서만** 보이고, 끝나면 버려짐.
- ThreadLocal = **스레드별 사물함**.
    - 같은 스레드라면 **어느 메서드에서든 꺼내볼 수 있음**.
    - 요청 전체(컨트롤러→서비스→리포지토리)처럼 **메서드를 여러 번 건너뛰는 경우**에 유리.

- 컨트롤러 → 서비스 → 리포지토리로 내려가면서 매번 `user`를 **파라미터로 전달**하려면 메서드 시그니처가 **전부 오염**됨. (오염 이란? 특정 값(`user`, `requestId` 등)을 실제로 **안 써도 되는** 많은 메서드들에 **억지로 파라미터로 추가·전달**)
- 게다가 **프레임워크 내부 로직**(예: 로깅, 트랜잭션, 보안)은 **내가 파라미터를 건낼 수 없는 곳**에서도 **같은 정보**를 써야 한다.

```jsx
// 모든 메서드에 사용자 정보를 전달해야 하는 상황
public void writePost(User user, String content) {
    service.savePost(user, content);
}
```

## 3. 동작을 사용하는 방법

**항상 `set → get → remove`** 흐름을 지켜야 하며, 특히 **스레드풀**에선 `finally { remove(); }`가 필수다.

```jsx
public class ThreadLocalBasic {

    // 1) 한 ThreadLocal 인스턴스 = 모든 스레드가 공유하는 "키 = REQ_ID"
    private static final ThreadLocal<String> REQ_ID = new ThreadLocal<String>() {
        @Override
        protected String initialValue() {
            return null; // get() 호출 시 기본값 -> 현재는 null
        }
    };

    // 2) 스레드마다 독립적으로 값을 set/get 한다
    static class Worker implements Runnable {
        private final String requestId;

        public Worker(String requestId) {
            this.requestId = requestId;
        }

        @Override
        public void run() {
            try {
                REQ_ID.set(requestId); // 현재 스레드의 사물함에 저장
                String v = REQ_ID.get(); // 현재 스레드의 사물함에서 꺼내기
                System.out.println(Thread.currentThread().getName() + " -> " + v);
            } finally {
                REQ_ID.remove(); // 중요: 스레드 끝/요청 끝에서 반드시 정리
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(new Worker("RID-A"), "T-1");
        Thread t2 = new Thread(new Worker("RID-B"), "T-2");
        t1.start(); t2.start();
        t1.join();  t2.join();
    }
}

```

## 4. Spring Security에서  사용한다?

Spring Security는 이 문제를 해결하기 위해 **`ThreadLocal`을 기본 전략**으로 사용합니다.

`SecurityContextHolder` 코드에서 이 내용을 확인할 수 있어요.

```jsx
// 시스템 설정이 없으면, 기본 전략은 "MODE_THREADLOCAL"
if (!StringUtils.hasText(strategyName)) {
    strategyName = "MODE_THREADLOCAL";
}

// "MODE_THREADLOCAL"이 선택되면, ThreadLocal을 사용하는 전략을 만듭니다.
if (strategyName.equals("MODE_THREADLOCAL")) {
    strategy = new ThreadLocalSecurityContextHolderStrategy();
}
```

1. **사용자 로그인**: 사용자가 로그인하면, Spring Security는 이 **사용자의 정보를 현재 스레드의 사물함(ThreadLocal)**에 넣어둡니다.
    - **코드**: `SecurityContextHolder.getContext().setAuthentication(인증정보);`
2. **어디서든 꺼내 쓰기**: 이제 `컨트롤러`, `서비스`, `리포지토리` 등 어느 계층이든 사용자 정보를 얻고 싶을 때, 인자를 전달받을 필요 없이 **자신의 사물함에서 바로 꺼내 쓸 수 있습니다.**
    - **코드**: `Authentication auth = SecurityContextHolder.getContext().getAuthentication();`

이 덕분에 개발자는 사용자 정보를 인자로 넘기는 번거로움 없이, 깔끔하게 코드를 작성할 수 있죠.

`ThreadLocal`은 **스레드 단위로 안전하게 데이터를 공유**하는 강력하고 실용적인 방법입니다.


출처
https://velog.io/@wken5577/Java-Thread-Local%EC%93%B0%EB%A0%88%EB%93%9C-%EB%A1%9C%EC%BB%AC%EC%9D%80-%EB%AC%B4%EC%97%87%EC%9D%BC%EA%B9%8C

https://www.baeldung.com/java-threadlocal?utm_source=chatgpt.com

https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html?utm_source=chatgpt.com