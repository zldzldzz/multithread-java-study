package thread.sync.test;
//결과를 예측해보자
import static util.MyLogger.*;

public class SyncTest2Main {

	public static void main(String[] args) throws InterruptedException {
		MyCounter counter = new MyCounter();

		Runnable task = new Runnable() {
			@Override
			public void run() {
				counter.count();
			}
		};

		Thread t1 = new Thread(task, "Thread-1");
		Thread t2 = new Thread(task, "Thread-2");

		t1.start();
		t2.start();
		t1.join();
		t2.join();


	}

	//지역변수의 경우 어떨까
	//지역변수는 스택이니까 애초에 독립적
	static class MyCounter {

		public void count() {
			int localValue =0;
			for (int i =0; i<1000; i++) {
				localValue = localValue + i;
			}
			log("결과 " + localValue);
		}
	}
}
