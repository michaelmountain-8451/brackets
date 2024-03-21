# Required files
1. teams.txt - should be a 64 line text file with the name of a single team on each line. Ordered by their appearance in the round of 64 (top to bottom, left to right)
2. results.txt - should be a 0-63 line text file. Each line containing the name of a team that won the corresponding tournament game (all first round games first in the same order as the teams.txt file, then all the second round games, etc. with the title game winner on line 63). Leave blank lines to indicate games not yet played.
3. points.txt - should be a 6-line text file. Each line containing the value of picking a game correctly in each round (first to last). Does not currently support variable points based on seeding.
4. ratings.csv - two-column CSV file containing the names of every team remaining in the tournament along with their strength rating (as determined by 538 - see [this link](https://fivethirtyeight.com/features/how-our-march-madness-predictions-work-2/) for details).
5. <bracket_folder>/<bracket_name>.txt - should be a 63-line text file. Each line containing the name of the team predicted to win the corresponding tournament game (matching the order of games in results.txt). Provide this file path as a runtime argument. The selected bracket will be evaluated against all other bracket files in its folder.
