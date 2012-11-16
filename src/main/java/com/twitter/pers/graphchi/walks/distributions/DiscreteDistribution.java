package com.twitter.pers.graphchi.walks.distributions;

import edu.cmu.graphchi.aggregators.ForeachCallback;
import edu.cmu.graphchi.aggregators.VertexAggregator;
import edu.cmu.graphchi.datablocks.IntConverter;
import edu.cmu.graphchi.util.IdCount;
import edu.cmu.graphchi.util.IdInt;

import java.util.*;

/**
 * Presents a map from integers to frequencies.
 * Special distributions for avoidance can be used to exclude
 * certain ids from the distributions in merges.
 * @author Aapo Kyrola, akyrola@twitter.com, akyrola@cs.cmu.edu
 */
public class DiscreteDistribution {

    private int[] ids;
    private int[] counts;
    private int uniqueCount;


    private DiscreteDistribution(int initialCapacity) {
        ids = new int[initialCapacity];
        counts = new int[initialCapacity];
        uniqueCount = initialCapacity;
    }


    public TreeSet<IdCount> getTop(int topN) {
        final TreeSet<IdCount> topList = new TreeSet<IdCount>(new Comparator<IdCount>() {
            public int compare(IdCount a, IdCount b) {
                return (a.count > b.count ? -1 : (a.count == b.count ? 0 : 1)); // Descending order
            }
        });
        IdCount least = null;
        for(int i=0; i<uniqueCount; i++) {
            if (topList.size() < topN) {
                topList.add(new IdCount(ids[i], counts[i]));
                least = topList.last();
            } else {
                if (counts[i] > least.count) {
                    topList.remove(least);
                    topList.add(new IdCount(ids[i], counts[i]));
                    least = topList.last();
                }
            }
        }
        return topList;
    }

    /**
     * Constructs a distribution from a sorted list of entries.
     * @param sortedIdList
     */
    public DiscreteDistribution(int[] sortedIdList) {
        if (sortedIdList.length == 0) {
            ids = new int[0];
            counts = new int[0];
            return;
        }
        if (sortedIdList.length == 1) {
            ids = new int[] {sortedIdList[0]};
            counts = new int[] {1};
            return;
        }

        /* First count unique */
        uniqueCount = 1;
        for(int i=1; i < sortedIdList.length; i++) {
            if (sortedIdList[i] != sortedIdList[i - 1]) {
                uniqueCount++;
            }
        }

        ids = new int[uniqueCount];
        counts = new int[uniqueCount];

        /* Create counts */
        int idx = 0;
        int curCount = 1;

        for(int i=1; i < sortedIdList.length; i++) {
            if (sortedIdList[i] != sortedIdList[i - 1]) {
                ids[idx] = sortedIdList[i - 1];
                counts[idx] = curCount;
                idx++;
                curCount = 1;
            }  else curCount++;
        }

        ids[idx] = sortedIdList[sortedIdList.length - 1];
        counts[idx] = curCount;
    }

    /**
     * Create a special avoidance distribution, where each count is -1.
     * @param avoids  sorted list of ids to avoid
     * @return
     */
    public static DiscreteDistribution createAvoidanceDistribution(int[] avoids) {
        DiscreteDistribution avoidDistr = new DiscreteDistribution(avoids);
        Arrays.fill(avoidDistr.counts, -1);
        return avoidDistr;
    }


    public int getCount(int id) {
        int idx = Arrays.binarySearch(ids, id);
        if (idx >= 0) {
            return counts[idx];
        } else {
            return 0;
        }
    }


    public int size() {
        return uniqueCount;
    }


    /**
     * Merge too distribution. If either has a negative entry for some id, it will
     * remain negative in the merged distribution (see createAvoidDistribution).
     * @param d1
     * @param d2
     * @return
     */
    public static DiscreteDistribution merge(DiscreteDistribution d1, DiscreteDistribution d2) {
        /* Merge style algorithm */

        /* 1. first count number of different pairs */
        int combinedUniqueIndices = 0;
        int leftIdx = 0;
        int rightIdx = 0;
        final int leftCount = d1.size();
        final int rightCount = d2.size();

        while(leftIdx < leftCount && rightIdx < rightCount) {
            if (d1.ids[leftIdx] == d2.ids[rightIdx]) {
                leftIdx++;
                rightIdx++;
                combinedUniqueIndices++;
            } else if (d1.ids[leftIdx] < d2.ids[rightIdx]) {
                combinedUniqueIndices++;
                leftIdx++;
            } else {
                combinedUniqueIndices++;
                rightIdx++;
            }
        }
        combinedUniqueIndices += (rightCount - rightIdx);
        combinedUniqueIndices += (leftCount - leftIdx);

        /* Create new merged distribution by doing the actual merge */
        DiscreteDistribution merged = new DiscreteDistribution(combinedUniqueIndices);
        leftIdx = 0;
        rightIdx = 0;
        int idx = 0;
        while(leftIdx < leftCount && rightIdx < rightCount) {
            if (d1.ids[leftIdx] == d2.ids[rightIdx]) {
                merged.ids[idx] = d1.ids[leftIdx];
                merged.counts[idx] = d1.counts[leftIdx] + d2.counts[rightIdx];

                // Force to retain negativity
                if (d1.counts[leftIdx] < 0 || d2.counts[rightIdx] < 0) {
                    merged.counts[idx] = -1;
                }
                leftIdx++;
                rightIdx++;
                idx++;

            } else if (d1.ids[leftIdx] < d2.ids[rightIdx]) {
                merged.ids[idx] = d1.ids[leftIdx];
                merged.counts[idx] = d1.counts[leftIdx];   // Note, retains negativity
                idx++;
                leftIdx++;
            } else {
                merged.ids[idx] = d2.ids[rightIdx];
                merged.counts[idx] = d2.counts[rightIdx];   // Note, retains negativity
                idx++;
                rightIdx++;
            }
        }

        while(leftIdx < leftCount) {
            merged.ids[idx] = d1.ids[leftIdx];
            merged.counts[idx] = d1.counts[leftIdx];
            leftIdx++;
            idx++;
        }

        while(rightIdx < rightCount) {
            merged.ids[idx] = d2.ids[rightIdx];
            merged.counts[idx] = d2.counts[rightIdx];
            rightIdx++;
            idx++;
        }
        return merged;
    }

}
