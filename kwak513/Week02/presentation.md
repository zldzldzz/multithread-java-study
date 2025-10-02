# 은행 예금, 출금 예제로 알아보는 동시성 문제

## 1. 주제
**은행 계좌 동시 예금 또는 출금 시 동시성 문제 발생과 synchronized를 이용한 해결**

---

## 2. 문제 제시
- 여러 스레드가 동시에 계좌를 조작할 때 **잔액 일관성이 깨질 수 있음**
- 예: 동시 출금 → 음수 잔액 발생 가능
- 원인: 여러 스레드가 동시에 접근해서는 안되는 임계 영역에 동시에 접근해서 발생
---

## 3. 실습 코드 (synchronized 없음)

```java
package presentation;

class Account {
    private long balance;

    public Account(long balance) {
        this.balance = balance;
    }

    // synchronized 제거
    public void deposit(long amount) {
        balance += amount;
        System.out.println(Thread.currentThread().getName() +
                " 입금: " + amount + ", 잔액: " + balance);
    }

    public void withdraw(long amount) {
        if (balance >= amount) {
            try { Thread.sleep(1); } catch (InterruptedException e) {} // 동시성 문제 유도
            balance -= amount;
            System.out.println(Thread.currentThread().getName() +
                    " 출금: " + amount + ", 잔액: " + balance);
        } else {
            System.out.println(Thread.currentThread().getName() +
                    " 출금 실패(잔액 부족): " + amount + ", 잔액: " + balance);
        }
    }

    public long getBalance() {
        return balance;
    }
}

public class BankSim {
    public static void main(String[] args) throws InterruptedException {
        Account acc = new Account(0); // 초기 잔액 0원

        // 입금 스레드
        Thread tDeposit = new Thread(new Runnable() {
            @Override
            public void run() {
                acc.deposit(50000);
            }
        }, "입금스레드");

        // 동시에 출금 두 스레드
        Thread tWithdraw1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    tDeposit.join(); // 입금 완료 대기
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                acc.withdraw(40000);
            }
        }, "출금스레드1");

        Thread tWithdraw2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    tDeposit.join(); // 입금 완료 대기
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                acc.withdraw(30000);
            }
        }, "출금스레드2");

        // 스레드 시작
        tDeposit.start();
        tWithdraw1.start();
        tWithdraw2.start();

        // main이 모두 끝날 때까지 대기
        tDeposit.join();
        tWithdraw1.join();
        tWithdraw2.join();

        System.out.println("최종 잔액: " + acc.getBalance());
    }
}

```

---

## 4. 예상 실행 결과 (race condition)

| 스레드 실행 순서              | 입금 | 출금1 | 출금2 | 최종 잔액 |
|-------------------------------|------|-------|-------|-----------|
| 입금 → 출금1 → 출금2          | 50000| 40000 | 30000 | -20000    |
| 입금 → 출금2 → 출금1          | 50000| 30000 | 40000 | -20000    |
| 입금 → 출금1 성공, 출금2 실패 | 50000| 40000 | 30000 | 10000     |
| ... (실행 시마다 달라짐)       |      |       |       |           |

> 동시성 문제 발생 → **스레드가 거의 동시에 실행되면서 작업 중간에 서로 끼어들 수 있어, 잔액이 음수로 변할 가능성이 있음**


---

## 5. 문제 원인
- withdraw 메서드에서 `balance >= amount` 확인 후, **스레드가 동시에 접근**할 수 있음
- CPU/OS 스케줄러가 **어떤 스레드를 먼저 실행할지 결정**  
- 결과: **두 스레드가 동시에 balance를 읽고 수정 → 음수 발생**  

---

## 6. 해결 방법: synchronized 적용

```java
public synchronized void deposit(long amount) { ... }
public synchronized void withdraw(long amount) { ... }
public synchronized long getBalance() { ... }
```

---
## 7. synchronized 적용 후 예상 결과

| 스레드 실행 순서        | 입금 | 출금1 | 출금2 | 최종 잔액 |
|-------------------------|------|-------|-------|-----------|
| 입금 → 출금1 → 출금2    | 50000| 40000 | 30000 | 10000     |
| 입금 → 출금2 → 출금1    | 50000| 30000 | 40000 | 20000     |
| 어떤 순서든 항상         |      |       |       | ≥ 0      |

> synchronized 적용 → 임계 영역에 한 번에 하나의 스레드만 접근 가능하므로, 어떤 순서로 실행되든 잔액이 음수가 되는 상황을 방지

---
## 8. synchronized 와 join 차이점
| 구분                  | synchronized                       | join                                         |
|-----------------------|-----------------------------------------|---------------------------------------------|
| 목적                  | 임계 영역에 동시에 한 스레드만 접근 가능 | 특정 스레드가 끝날 때까지 기다림            |
| 적용 대상             | 임계 영역(공유 자원) 보호               | 스레드 간 순서 제어                          |
| 동시성 문제 해결 여부  | 모든 동시 접근 상황에서 안전하게 해결   | 순서가 고정된 상황에서만 일부 문제 해결 가능    |
| 사용 난이도           | 비교적 직관적, 한 번 설정으로 안전     | 스레드가 많아지면 관리 어려움                |
| 스레드 실행 강제      | 접근 자체를 막아 안전하게 실행         | 기다림만 강제, 임계 영역 직접 보호 X        |

