package nextstep.subway.path;

import nextstep.subway.auth.domain.LoginMember;
import nextstep.subway.auth.domain.OptionalLoginMember;
import nextstep.subway.common.Fare;
import nextstep.subway.line.domain.Distance;
import nextstep.subway.line.domain.Line;
import nextstep.subway.line.domain.LineRepository;
import nextstep.subway.line.domain.Section;
import nextstep.subway.path.application.PathCalculateException;
import nextstep.subway.path.application.PathService;
import nextstep.subway.path.dto.PathRequest;
import nextstep.subway.path.dto.PathResponse;
import nextstep.subway.station.domain.Station;
import nextstep.subway.station.domain.StationRepository;
import nextstep.subway.station.dto.StationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutsideInPathServiceTest {

	@InjectMocks
	private PathService pathService;

	@Mock
	private LineRepository lineRepository;

	@Mock
	private StationRepository stationRepository;

	/**
	 *              거리 5
	 * 교대역    --- *2호선* ---   강남역
	 * |                        |
	 * *3호선*                   *신분당선*
	 * 거리 3                     거리 10
	 * |                        |
	 * 남부터미널역  --- *3호선* --- 양재
	 *              거리 2
	 */

	private Station 강남역;
	private Station 남부터미널역;
	private Station 양재역;
	private Station 교대역;
	private Line 신분당선;
	private Line 이호선;
	private Line 삼호선;

	@BeforeEach
	void setUp() {
		강남역 = mockStation(1L, "강남역");
		남부터미널역 = mockStation(2L, "남부터미널역");
		양재역 = mockStation(3L, "양재역");
		교대역 = mockStation(4L, "교대역");

		신분당선 = mockLine("신분당선", 1000, Arrays.asList(mockSection(강남역, 양재역, 10)));
		이호선 = mockLine("이호선", 0, Arrays.asList(mockSection(교대역, 강남역, 5)));
		삼호선 = mockLine("삼호선", 200, Arrays.asList(
				mockSection(교대역, 남부터미널역, 3), mockSection(남부터미널역, 양재역, 2)));

		given(lineRepository.findAll()).willReturn(Arrays.asList(신분당선, 이호선, 삼호선));
		given(stationRepository.findById(강남역.getId())).willReturn(Optional.of(강남역));
		given(stationRepository.findById(양재역.getId())).willReturn(Optional.of(양재역));
		given(stationRepository.findById(남부터미널역.getId())).willReturn(Optional.of(남부터미널역));
		given(stationRepository.findById(교대역.getId())).willReturn(Optional.of(교대역));
	}

	private Station mockStation(Long id, String name) {
		Station station = mock(Station.class);
		given(station.getId()).willReturn(id);
		given(station.getName()).willReturn(name);
		return station;
	}

	private Line mockLine(String name, int fare, List<Section> sections) {
		Line line = mock(Line.class);
		given(line.getName()).willReturn(name);
		given(line.getFare()).willReturn(new Fare(fare));
		given(line.getSections()).willReturn(sections.iterator());
		sections.forEach(section -> given(section.getLine()).willReturn(line));
		return line;
	}

	private Section mockSection(Station upStation, Station downStation, int distance) {
		Section section = mock(Section.class);
		given(section.getUpStation()).willReturn(upStation);
		given(section.getDownStation()).willReturn(downStation);
		given(section.getDistance()).willReturn(new Distance(distance));
		return section;
	}

	@DisplayName("로그인 된 사용자로 경로를 구할시 나이를 사용하여 요금을 구한다.")
	@Test
	void calculatePath_로그인사용자() {
		// given
		given(stationRepository.findAllByIdIn(anyList())).willReturn(Arrays.asList(강남역, 양재역));
		LoginMember loginMember = mock(LoginMember.class);
		given(loginMember.getAge()).willReturn(22);
		OptionalLoginMember optionalLoginMember = new OptionalLoginMember(loginMember);

		// when
		PathRequest pathRequest = new PathRequest(강남역.getId(), 양재역.getId());
		PathResponse pathResponse = pathService.calculatePath(optionalLoginMember, pathRequest);

		// then
		assertThat(pathResponse.getStations())
				.map(StationResponse::getName)
				.containsExactly("강남역", "양재역");
		verify(loginMember, times(1)).getAge();
	}

	@DisplayName("로그인 되지 않은 상태로도 경로를 구할 수 있다.")
	@Test
	void calculatePath_비로그인사용자() {
		// given
		given(stationRepository.findAllByIdIn(anyList())).willReturn(Arrays.asList(강남역, 남부터미널역));
		OptionalLoginMember optionalLoginMember = OptionalLoginMember.notFound();

		// when
		PathRequest pathRequest = new PathRequest(강남역.getId(), 남부터미널역.getId());
		PathResponse pathResponse = pathService.calculatePath(optionalLoginMember, pathRequest);

		// then
		assertThat(pathResponse.getStations())
				.map(StationResponse::getName)
				.containsExactly("강남역", "교대역", "남부터미널역");
	}

	@DisplayName("존재하지 않는 역으로 경로를 구하면 예외가 발생한다.")
	@Test
	void calculatePath_NotExistStation() {
		// given
		given(stationRepository.findById(anyLong())).willReturn(Optional.empty());
		final OptionalLoginMember optionalLoginMember = OptionalLoginMember.notFound();

		// when
		PathRequest pathRequest = new PathRequest(1L, 2L);
		assertThatThrownBy(() -> pathService.calculatePath(optionalLoginMember, pathRequest))
				.isInstanceOf(PathCalculateException.class)
				.hasMessageContaining("존재하지 않는");
	}
}