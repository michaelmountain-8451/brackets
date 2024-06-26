package org.mountm.brackets;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BracketRunner {
	
	private static final List<String> teams = new ArrayList<>();
	private static final DecimalFormat df = new DecimalFormat("#.###");
	private static final Set<String> stillAliveTeams = new HashSet<>();
	private static String myBracketFileName = "bracket.txt";

	public static void main(String[] args) {
		
		long[][] counts = new long[Tournament.NUM_TEAMS][Tournament.NUM_ROUNDS];
		long[][] wins = new long[Tournament.NUM_TEAMS][Tournament.NUM_ROUNDS];
		
		readTeams();
		Tournament tourney = readResults();
		long realMask = tourney.getGamesMask();
		long realResults = tourney.getResults();
		// restrict the search space by leaving off one bit for each game at the beginning of the bracket that is already completed
		int shift = Long.numberOfTrailingZeros(~realMask);
		Bracket myBracket = readMyBracket(args);
		System.out.println("Current score: " + tourney.scoreBracket(myBracket));
		List<Bracket> otherBrackets = readOtherBrackets();
		otherBrackets.sort(Comparator.comparingInt(tourney::scoreBracket).reversed());

		Map<String, Double> teamRatings = readTeamRatings();
		stillAliveTeams.addAll(teamRatings.keySet());
		Set<Integer> stillAliveTeamIndices = stillAliveTeams.stream().map(teams::indexOf).collect(Collectors.toSet());
		double[][] probabilities = generateProbabilities(teamRatings);
		
		long max = 0L;
		long min = 0L;
		for (int i = 0; i < Tournament.NUM_TEAMS - 1 - shift; i++) {
			max = (max << 1) + 1;
		}
		System.out.println("Checking " + (max + 1) +  " scenarios.");
		long endMask = 0L;
		// keep a static mask of 1s for all games from the beginning of tournament
		// used to merge real results with hypothetical results during the search
		for (int i = 0; i < shift; i++) {
			endMask = (endMask << 1) + 1;
		}
		if (max < 0) {
			max = Long.MAX_VALUE;
			min = Long.MIN_VALUE;
		}

		long startTime = System.currentTimeMillis();

		long count = 0L;
		long winCount = 0L;
		long results;
		long shiftedFakeResults;
		double winProb = 0;
		for (long fakeResults = min; fakeResults <= max; fakeResults++) {
			if (fakeResults % 10000000 == 0) {
				System.out.println(df.format((double) fakeResults / max) + " checked in " + (System.currentTimeMillis() - startTime) / 1000 + " seconds");
			}
			shiftedFakeResults = fakeResults << shift;
			// https://graphics.stanford.edu/~seander/bithacks.html#MaskedMerge
			results = shiftedFakeResults ^ ((shiftedFakeResults ^ realResults) & endMask);
			// still check against real results in case there are some reported results outside the trailing bits
			if ((results & realMask) == realResults) {
				// add to counts for any matching team advancement scenarios
				for (int i = 0; i < Tournament.NUM_TEAMS; i++) {
					for (int j = 0; j < Tournament.NUM_ROUNDS; j++) {
						if ((results & Tournament.getMasks()[i][j]) == Tournament.getValues()[i][j]) {
							counts[i][j]++;
						}
					}
				}
				count++;
				tourney.setResults(results, max);
				int myScore = tourney.scoreFinishedBracket(myBracket);
				if (otherBrackets.stream().anyMatch(b -> tourney.scoreFinishedBracket(b) > myScore)) {
					continue;
				}
				// figure out the probability of this outcome happening
				double scenarioProb = 1.0;
				for (int i : stillAliveTeamIndices) {
					for (int j : stillAliveTeamIndices) {
						if (i != j && (results & Tournament.getH2HMasks()[i][j]) == Tournament.getH2HValues()[i][j]) {
							scenarioProb *= probabilities[i][j];
						}
					}
				}
				winProb += scenarioProb;
				
				// add to wins for any matching team advancement scenarios
				for (int i = 0; i < Tournament.NUM_TEAMS; i++) {
					for (int j = 0; j < Tournament.NUM_ROUNDS; j++) {
						if ((results & Tournament.getMasks()[i][j]) == Tournament.getValues()[i][j]) {
							wins[i][j]++;
						}
					}
				}
				winCount++;
			}
		}
		double winRate = (double) winCount / count;
		System.out.println("You win " + winCount + " out of " + count + " different scenarios: " + df.format(winRate));
		System.out.println("Your chances of winning are " + df.format(winProb));
		if (winCount > 0) {
			// print out any team advancement scenarios that result in improved overall odds
			// TODO figure out a way to sort these and display the most important first
			for (int i = 0; i < Tournament.NUM_TEAMS; i++) {
				for (int j = 0; j < Tournament.NUM_ROUNDS; j++) {
					if (wins[i][j] == counts[i][j] && wins[i][j] > 0) {
						System.out.println("Clinch win with " + teams.get(i) + " winning in round " + (j+1));
					} else if (wins[i][j] == 0 && counts[i][j] > 0){
						System.out.println("Eliminated with " + teams.get(i) + " winning in round " + (j+1));
					} else if ((double) wins[i][j] / counts[i][j] > winRate) {
						System.out.println(teams.get(i) + " winning in round " + (j+1) + " gives win rate " + df.format((double) wins[i][j] / counts[i][j]));
					}
				}
			}
		}
		long endTime = System.currentTimeMillis();
		
		System.out.println("Execution time: " + (endTime - startTime) / 1000 + " seconds");

	}

	private static double[][] generateProbabilities(Map<String, Double> teamRatings) {
		double[][] probabilities = new double[Tournament.NUM_TEAMS][Tournament.NUM_TEAMS];
		for (String team1 : stillAliveTeams) {
			for (String team2 : stillAliveTeams) {
				if (!team1.equals(team2)) {
					probabilities[teams.indexOf(team1)][teams.indexOf(team2)] = winProb(teamRatings.get(team1), teamRatings.get(team2));
				}
			}
		}
		return probabilities;
	}

	private static double winProb(Double v1, Double v2) {
		// https://fivethirtyeight.com/features/how-our-march-madness-predictions-work-2/
		return 1.0 / (1.0 + Math.pow(10, (v2 - v1) * 0.07616));
	}

	private static Map<String, Double> readTeamRatings() {
		Map<String, Double> retVal = new HashMap<>();
		try (BufferedReader br = new BufferedReader(new FileReader("ratings.csv"))) {
			String currentLine;
			while ((currentLine = br.readLine()) != null) {
				String[] data = currentLine.split(",");
				retVal.put(data[0], Double.parseDouble(data[1]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return retVal;
	}

	private static Tournament readResults() {
		List<String> winners = new ArrayList<>();
		int[] gamePointsPerRound = new int[Tournament.NUM_ROUNDS];
		try (BufferedReader br = new BufferedReader(new FileReader("points.txt"))) {
			String currentLine;
			int line = 0;
			while ((currentLine = br.readLine()) != null) {
				gamePointsPerRound[line++] = Integer.parseInt(currentLine, 10);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Tournament tourney = new Tournament(gamePointsPerRound);
		try (BufferedReader br = new BufferedReader(new FileReader("results.txt"))) {
			String currentLine;
			while ((currentLine = br.readLine()) != null) {
				winners.add(currentLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		long results = convertToResults(winners, false);
		long gamesMask = convertToGamesMask(winners);
		tourney.setResults(results, gamesMask);
		return tourney;
	}

	private static long convertToGamesMask(List<String> winners) {
		long result = 0L;
		int shift = 0;
		for (String winner: winners) {
			if (teams.contains(winner)) {
				result |= 1L << shift;
			}
			shift++;
		}
		System.out.println(Long.toBinaryString(result));
		return result;
	}

	private static long convertToResults(List<String> winners, boolean shouldFailOnBlanks) {
		long result = 0L;
		Set<String> winningTeams = new HashSet<>(winners);
		for (String team: winningTeams) {
			int teamIdx = teams.indexOf(team);
			if (teamIdx == -1) {
				if (shouldFailOnBlanks || !"".equals(team)) {
					throw new RuntimeException("Couldn't find team " + team);
				} else continue;
			}
			int numWins = (int) winners.stream().filter(w -> w.equals(team)).count();
			long teamMask = Tournament.getMasks()[teamIdx][numWins-1];
			long teamVal = Tournament.getValues()[teamIdx][numWins-1];
			// https://graphics.stanford.edu/~seander/bithacks.html#MaskedMerge
			result = result ^ ((result ^ teamVal) & teamMask);
		}
		return result;
	}

	private static List<Bracket> readOtherBrackets() {
		List<Bracket> brackets = new ArrayList<>();
		File myBracket = new File(myBracketFileName);
		if (myBracket.exists() && myBracket.getParentFile() != null) {
			File[] otherBracketFiles = myBracket.getParentFile().listFiles((dir, name) -> !myBracketFileName.endsWith(name));
			assert otherBracketFiles != null;
			for (File file : otherBracketFiles) {
				try (BufferedReader br = new BufferedReader(new FileReader(file))) {
					brackets.add(readBracket(br));
				} catch (IOException e) {
					e.printStackTrace();
				} catch (RuntimeException e) {
					System.out.println("Error reading " + file.getName());
					throw e;
				}
			}
		}
		return brackets;
	}

	private static Bracket readMyBracket(String[] args) {
		Bracket bracket;
		myBracketFileName = args[0];
		try (BufferedReader br = new BufferedReader(new FileReader(myBracketFileName))) {
			bracket = readBracket(br);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (RuntimeException e) {
			System.out.println("Error reading " + myBracketFileName);
			throw e;
		}
		return bracket;
	}
	
	private static Bracket readBracket(BufferedReader br) {
		String team;
		List<String> winners = new ArrayList<>();
		try {
			while ((team = br.readLine()) != null) {
				winners.add(team);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bracketFromWinners(winners);
	}


	private static void readTeams() {
		String currentLine;
		try (BufferedReader br = new BufferedReader(new FileReader("teams.txt"))) {
			while ((currentLine = br.readLine()) != null) {
				teams.add(currentLine);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static Bracket bracketFromWinners(List<String> winners) {
		Bracket bracket = new Bracket();
		bracket.setPicks(convertToResults(winners, true));
		return bracket;
	}

}
