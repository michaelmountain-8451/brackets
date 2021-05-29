package org.mountm.brackets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BracketRunner {
	
	private static List<String> teams = new ArrayList<>();
	private static DecimalFormat df = new DecimalFormat("#.##");

	public static void main(String[] args) {
		
		long[][] counts = new long[Tournament.NUM_TEAMS][Tournament.NUM_ROUNDS];
		long[][] wins = new long[Tournament.NUM_TEAMS][Tournament.NUM_ROUNDS];
		
		readTeams();
		Tournament tourney = readResults();
		long realMask = tourney.getGamesMask();
		long realResults = tourney.getResults();
		int shift = Long.numberOfTrailingZeros(~realMask);
		Bracket myBracket = readMyBracket();
		System.out.println("Current score: " + tourney.scoreBracket(myBracket));
		Set<Bracket> otherBrackets = readOtherBrackets();
		
		long max = 0L;
		long min = 0L;
		// restrict the search space by leaving off one bit for each game at the beginning of the bracket that is already completed
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
		long results = 0L;
		long shiftedFakeResults = 0L;
		for (long fakeResults = min; fakeResults <= max; fakeResults++) {
			if (fakeResults % 500000000 == 0) {
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
				if (tourney.scoreFinishedBracket(myBracket) > otherBrackets.stream().mapToInt(tourney::scoreFinishedBracket).max().orElse(0)) {
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
		}
		System.out.println("You win " + winCount + " out of " + count + " different scenarios: " + df.format((double) winCount / count));
		if (winCount > 0) {
			double winRate = (double) winCount / count;
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

	private static Tournament readResults() {
		List<String> winners = new ArrayList<>();
		Tournament tourney = new Tournament();
		String currentLine;
		try (BufferedReader br = new BufferedReader(new FileReader("results.txt"))) {
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

	private static long convertToResults(List<String> winners, boolean failIfNotFound) {
		long result = 0L;
		Set<String> winningTeams = new HashSet<>(winners);
		for (String team: winningTeams) {
			int teamIdx = teams.indexOf(team);
			if (teamIdx == -1) {
				if (failIfNotFound || !"".equals(team)) {
					throw new RuntimeException("Couldn't find team " + team);
				} else continue;
			}
			int numWins = (int) winners.stream().filter(w -> w.equals(team)).count();
			long teamMask = Tournament.getMasks()[teamIdx][numWins-1];
			long teamVal = Tournament.getValues()[teamIdx][numWins-1];
			// https://graphics.stanford.edu/~seander/bithacks.html#MaskedMerge
			result = result ^ ((result ^ teamVal) & teamMask);
		}
		System.out.println(Long.toBinaryString(result));
		return result;
	}

	private static Set<Bracket> readOtherBrackets() {
		Set<Bracket> brackets = new HashSet<>();
		File f = new File("other_brackets");
		if (f.exists()) {
			File[] files = f.listFiles();
			for (int i = 0; i < files.length; i++) {
				try (BufferedReader br = new BufferedReader(new FileReader(files[i]))) {
					brackets.add(readBracket(br));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return brackets;
	}

	private static Bracket readMyBracket() {
		Bracket bracket = new Bracket();
		try (BufferedReader br = new BufferedReader(new FileReader("bracket.txt"))) {
			bracket = readBracket(br);
		} catch (IOException e) {
			e.printStackTrace();
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
