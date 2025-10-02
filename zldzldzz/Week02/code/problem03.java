package thread.sync.test;

// 다음에서 value 필드(멤버 변수)는 공유되는 값이다.
// 멀티스레드 상황에서 문제가 될 수 있을까?
class Immutable {
	private final int value;
	public Immutable(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}
}
/* 여러 스레드가 같은 공유 자원을 접근할때 그것을 고려하지 않고 사용하면 문제가 된다.
 * 스레드가 값을 변경하는 중의 다른 스레드가 값을 변경하다면 문제가 될 수 있지만
 * 값을 공유만 하고 서로 수정을 하지 않는다면 문제가 되지 않는다.
 */