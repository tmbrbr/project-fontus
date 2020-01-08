package de.tubs.cs.ias.asm_test.taintaware;

import de.tubs.cs.ias.asm_test.taintaware.range.IASTaintRangeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IASTaintInformation {
    private List<IASTaintRange> ranges = new ArrayList<>(1);

    public synchronized IASTaintInformation addRange(int start, int end, short sourceId) {
        if (start == end) {
            // No need to process ranges with length 0
            return this;
        }

        final IASTaintRange newRange = new IASTaintRange(start, end, sourceId);

        final int containedOrAdjacentTo_index = getListIndexOfFirstContainingOrAdjacentRange(start);

        int rightNeighbour_index = containedOrAdjacentTo_index;

        // Is the start contained in another range?
        if (containedOrAdjacentTo_index >= 0) {
            final IASTaintRange containedOrAdjacentToOrig = this.ranges.get(containedOrAdjacentTo_index);
            final IASTaintRange containedOrAdjacentTo = new IASTaintRange(containedOrAdjacentToOrig.getStart(), start, containedOrAdjacentToOrig.getSource());
            final int containedOrAdjacentToEnd = this.ranges.get(containedOrAdjacentTo_index).getEnd();

            // Remove the original range in case it got shrinked down to a length of zero (start == containedOrAdjaventTo.end, this might be some instructions faster)
            if (containedOrAdjacentTo.getStart() == start) {
                ranges.remove(containedOrAdjacentTo_index);
                rightNeighbour_index--;
            } else {
                // replace the immutable range with its shrinked version
                this.ranges.set(containedOrAdjacentTo_index, containedOrAdjacentTo);
            }
            // is the end of the new range in the same, already existing, range? If so it is completely contained in it
            // and we have to split it - we can also skip the rest of the algorithm because there are no neighbours for sure
            if (end <= containedOrAdjacentToEnd) {
                final IASTaintRange secondHalf = new IASTaintRange(end, containedOrAdjacentToEnd, containedOrAdjacentTo.getSource());

                this.ranges.add(rightNeighbour_index + 1, newRange);

                // we do not add the second half range if its length is zero
                if (secondHalf.getStart() < secondHalf.getEnd()) {
                    ranges.add(rightNeighbour_index + 2, secondHalf);
                }

                return this;
            }

            // not completely contained, shift rightNeighbour_index to actual right neighbour
            rightNeighbour_index++;
        } else {
            // containedOrAdjacentTo_index contains a negative value; its inverse would be the index where to insert the new range
            // in order to keep the list sorted
            rightNeighbour_index = containedOrAdjacentTo_index * -1 - 1;
        }

        // Are there any (more) neighbours on the right?
        while (rightNeighbour_index < this.ranges.size()) {
            final IASTaintRange rightNeighbour = this.ranges.get(rightNeighbour_index);

            if (rightNeighbour.getEnd() <= end) {
                // right neighbour is completely covered by new range
                ranges.remove(rightNeighbour_index);
            } else if (rightNeighbour.getStart() < end) {
                // it is at least partially covered by the new range.
                // we have to terminate the loop because we do not need to go the right anymore
                this.ranges.set(rightNeighbour_index, new IASTaintRange(end, rightNeighbour.getEnd(), rightNeighbour.getSource()));
                break;
            } else {
                // right neighbour is not even partially covered, i.e. there must be a gap between the range we partially covered and this neighbour.
                // We will not touch the neighbour at all and will terminate the loop because we do not need to go the right anymore
                break;
            }
        }

        ranges.add(rightNeighbour_index, newRange);

        return this;
    }

    public IASTaintInformation replaceTaintInformation(int start, int end, List<IASTaintRange> newRanges) {
        // TODO Implement
        throw new UnsupportedOperationException("Implement!");
    }

    private int getListIndexOfFirstContainingOrAdjacentRange(int index) {
        return Collections.binarySearch(this.ranges, null, (range, irrelevant) -> {
            if(range.getStart() <= index && range.getEnd() > index) {
                return 0;
            } else if (range.getStart() > index) {
                return 1;
            } else {
                return -1;
            }
        });
    }

    public boolean isTainted() {
        return ranges.isEmpty();
    }

    public void removeAll() {
        this.ranges.clear();
    }

    public synchronized void appendRangesFrom(IASTaintInformation other) {
        this.appendRangesFrom(other, 0);
    }

    public synchronized void appendRangesFrom(IASTaintInformation other, int rightShift) {
        if (rightShift == 0) {
            ranges.addAll(other.ranges);
            return;
        }
        // TODO

    }

    public synchronized void appendRanges(List<IASTaintRange> ranges) {
        this.ranges.addAll(ranges);
    }

    /**
     * Finds all ranges which at least lie partly within the specified interval
     *
     * @param startIndex including
     * @param endIndex   excluding
     */
    public List<IASTaintRange> getRanges(int startIndex, int endIndex) {
        if (endIndex < startIndex || startIndex < 0) {
            throw new IndexOutOfBoundsException("startIndex: $startIndex, endIndex: $endIndex");
        } else if (endIndex == startIndex) {
            return new ArrayList<>(0);
        }

        List<IASTaintRange> affectedRanges = new ArrayList<>(this.ranges.size());

        for (IASTaintRange range : this.ranges) {
            if (range.getEnd() < startIndex || endIndex <= range.getStart()) {
                // Outside range
                continue;
            }

            affectedRanges.add(range);
        }
        return affectedRanges;
    }

    public List<IASTaintRange> getAllRanges() {
        return new ArrayList<>(this.ranges);
    }
}
