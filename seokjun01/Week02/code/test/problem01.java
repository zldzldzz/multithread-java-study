// 의문 ,, run() 메소드에 synchronized 붙이는 것과 외부 클래스의 메소드에 붙이는 것
// 둘 다 결과는 같다
// 뭔 차이일까욧

package thread.sync.test;

public class SyncTest1Main {

	public static void main(String[] args) throws InterruptedException {
		Counter counter = new Counter();

		//익명클래스로 한 번에 스레드 정의
		Runnable task = new Runnable() {
			@Override
			public void run() {
				for (int i=0; i<10000; i++){
					counter.increment();
				}
			}
		};

		Thread t1 = new Thread(task);
		Thread t2 = new Thread(task);

		t1.start();
		t2.start();
		t1.join();
		t2.join();
		System.out.println("결과: " + counter.getCount());

	}

	//공유 자원
	static class Counter {
		private int count = 0;

		public synchronized void increment() {
			count = count + 1;
		}

		public synchronized int getCount() {
			return count;
		}
	}
}
