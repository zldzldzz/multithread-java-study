# Collection 인터페이스(Java Collection Framework)
<img width="894" height="600" alt="Image" src="https://github.com/user-attachments/assets/c4e13f39-a2bc-409f-8baa-4e2ecc1a9b85" />

`Map은 JCF의 핵심이지만 Collection의 자식이 아닌 별도 축입니다`

## Collection은 다양한 자료구조들의 부모인 인터페이스
Map을 제외한 List, Queue, Set을 상속하는 실질적 최상위 컬렉션 타입으로 업캐스팅으로 다양한 종류의 컬렉션 자료형을 받아 삽입, 삭제, 탐색이 가능하다

### Collection 공통 연산을 선언하는 인터페이스
size, isEmpty, contains, add, remove, addAll, removeAll, retainAll, clear, iterator, toArray

### Object 클래스는?
Object는 java.lang 패키지에 있는 클래스이며, Java의 모든 클래스가 암묵적으로 상속받는 최상위 부모 클래스입니다.
다형성: Object 타입은 모든 종류의 객체(인스턴스)를 참조할 수 있는 가장 광범위한 타입입니다.


### Collection의 부모인 Iterable과 Map 들을 포함하는 JCF를 사용하는 이유

- 인터페이스와 다형성을 이용한 객체지향적 설계를 통해 표준화되어 있기 때문에, 사용법을 익히기에도 편리하고 재사용성이 높다.
- 데이터 구조 및 알고리즘의 고성능 구현을 제공하여 프로그램의 성능과 품질을 향상시킨다.
- 관련 없는 API 간의 상호 운용성을 제공한다. (상위 인터페이스 타입으로 업캐스팅하여 사용)
- 이미 구현되어있는 API를 사용하면 되기에, 새로운 API를 익히고 설계하는 시간이 줄어든다.
- 소프트웨어 재사용을 촉진한다. 만일 자바에서 지원하지 않는 새로운 자료구조가 필요하다면, 컬렉션들을 재활용하여 조합하여 새로운 알고리즘을 만들어낼 수 있다.

# Collections 클래스
Collections는 Collection의 다형성을 사용하고 static을 통해서 아래 같은 다양한 동작들을 담고 있는 알고리즘의 집합인 클래스

## Collections가 제공하는 알고리즘

### 정렬
ex) sort(list) : 지정된 리스트(List)의 요소들을 자연적인 순서 (예: 숫자, 알파벳 순)에 따라 오름차순으로 정렬합니다.

### 탐색
ex) binarySearch(list, key) : 반드시 사전에 정렬되어 있는 리스트(List)에서 지정된 key를 이진 탐색 (Binary Search)으로 찾습니다. 요소의 인덱스를 반환하며, 찾지 못하면 음수의 숫자를 반환합니다.

### 기타 조작 
ex) shuffle(list) : 지정된 리스트(List) 요소들을 무작위 순서로 섞습니다 (랜덤하게 배열).
reverse(list) : 지정된 리스트(List) 요소들의 순서를 뒤집습니다. (첫 번째 요소는 마지막으로, 마지막 요소는 처음으로)

### 집계
ex) max(coll) : 주어진 컬렉션(Collection)에서 가장 큰 (최대) 요소를 자연적인 순서에 따라 찾아서 반환합니다.

### 복사 및 초기화
ex) fill(list, value) : 지정된 리스트(List)의 모든 요소를 지정된 value로 대체합니다. (리스트의 크기는 유지됩니다.)
copy(dest, src) : 소스 리스트(src)의 모든 요소를 대상 리스트(dest)로 복사합니다. 대상 리스트의 크기는 소스 리스트의 크기보다 크거나 같아야 합니다.

### 검사
checkedList(list, type) : 주어진 리스트(list)를 감싸는 래퍼(Wrapper) 리스트를 반환합니다. 이 리스트는 실행 시간에 지정된 타입(type) 외의 요소가 추가되는 것을 방지하여 타입 안전성을 높여줍니다. (컬렉션에 올바른 타입만 들어갔는지 확인하는 역할)

### 동기화
강의 영상에서 나온 것 같이 아래 같이 자료형 자체의 synchronized를 통해서 동시성 문제를 해결하는 안전한 자료형을 만들 수 있습니다.
List<Integer> sync = Collections.synchronizedList(new ArrayList<>())