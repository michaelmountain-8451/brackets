package org.mountm.brackets;

public class Bracket {
	
	private long picks;
	
	public Bracket() {
		picks = 0L;
	}
	
	public boolean getPick(int position) {
		long mask = 1L << position;
		return (picks & mask) != 0;
	}
	
	public void setPick(boolean pick, int position) {
		if (pick) {
			picks |= 1L << position;
		}
	}
	
	public long getPicks() {
		return picks;
	}
	
	public void setPicks(long picks) {
		this.picks = picks;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (picks ^ (picks >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Bracket other = (Bracket) obj;
		if (picks != other.picks)
			return false;
		return true;
	}

}
