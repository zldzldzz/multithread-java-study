# 📚 2주차 - 섹션 5

## 1. 스레드 인터럽트 (Interrupt)
인터럽트는 대기 상태의 스레드를 직접 깨워 Runnable 상태로 만드는 기능이다.
main 스레드에서 interrupt()를 호출하면, 대상 스레드는 InterruptedException을 발생시키고 catch 블록으로 넘어가게 된다. 이는 스레드의 상태 변화 흐름을 이해하는 데 중요.

* 인터럽트 상태 확인과 예외:
  while 반복문 안에서 Thread.sleep() 같은 대기 메서드를 호출할 때, interrupt()가 걸리면 InterruptedException이 발생한다.
  자바는 인터럽트 예외가 한 번 터지고 나면, 스레드의 인터럽트 상태 플래그를 원래 상태인 false로 되돌려준다.

### Thread.interrupted() vs Thread.isInterrupted()
* Thread.interrupted(): 이 메서드는 스레드의 인터럽트 상태를 확인하여 true를 반환하고, 동시에 인터럽트 상태를 false로 변경한다. 인터럽트의 목적(스레드 중단 등)을 달성했다면 상태를 정상으로 되돌리는 데 유용하다.
* Thread.isInterrupted(): 이 메서드는 스레드의 인터럽트 상태만 확인하고, 상태를 변경하지 않는다.

## 2. 스레드 양보 (Yield)
Yield는 실행 중인 스레드가 덜 바쁜 다른 대기 스레드에게 CPU 실행 기회를 자발적으로 양보하는 개념.

### yield()와 sleep()의 차이점
Thread.sleep()을 사용하면 스레드 상태가 아주 잠시 RUNNABLE에서 TIMED_WAITING으로 변경된다.
이는 CPU 자원을 사용하지 않고 잠시 실행 스케줄링에서 제외되지만, 다시 RUNNABLE 상태로 돌아오는 복잡한 과정을 거친다.
이는 양보할 필요가 없는 상황에서도 불필요한 대기를 유발할 수 있음.
반면, yield()는 현재 실행 중인 스레드가 자발적으로 CPU를 양보하여 다른 스레드가 실행될 수 있도록 한다.
중요한 점은 **Runnable 상태를 유지(Ready로 변경)**한다는 것. 따라서 양보할 스레드가 없다면 본인 스레드가 계속 실행된다 .

* sleep(): 스레드를 TIMED_WAITING 상태로 변경 (대기)
* yield(): 스레드를 RUNNABLE(Ready) 상태로 유지 (양보)

수천만, 수억 번 반복되는 작업에서는 이러한 작은 최적화가 큰 성능 차이를 유발할 수 있음.