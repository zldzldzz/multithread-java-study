class Immutable {

	private final int value;
	public Immutable(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

}
//다음에서 `value` 필드(멤버 변수)는 공유되는 값이다. 멀티스레드 상황에서 문제가 될 수 있을까?
// 내 답 : 상수는 힙 영역에 따로 상수 풀이 존재하는 것으로 암 같이 참조하므로 문제 생긴다에 한 표  (-> 오답)

// 정답 : 불변 final이므로 공유해도 안전하다

