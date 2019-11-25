package co.elastic.g1;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrate that G1 can have high memory usage for an extended duration, i.e., not only because of concurrency or a concurrent GC
 * that has not run to completion.
 * <p/>
 * The test case does following:
 * <ul>
 *     <li>ensures we have old/referenced data with reasonable graph complexity</li>
 *     <li>allocates a burst of primarily humongous objects until we reach the limit</li>
 *     <li>then wait for a while before outputting anything, ensuring that any concurrent GC has a change to complete</li>
 * </ul>
 *
 * Run this program with following arguments:
 * <pre>-Xmx8g -Xms8g -XX:+UseG1GC -XX:G1ReservePercent=25 -XX:InitiatingHeapOccupancyPercent=30
 * -Xlog:gc*,gc+age=trace,gc+ihop=trace,gc+heap=trace,gc+humongous=trace,gc+phases=trace,safepoint:file=gc.log:utctime,pid,tags:filecount=32,filesize=64m</pre>
 */
public class HighHeap {
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();

    public static void main(String[] args) {
        try {
            dorun();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void dorun() throws InterruptedException {
        long max = MEMORY_MX_BEAN.getHeapMemoryUsage().getMax();
        long begin = currentMemoryUsage();
        // guess at 10 percent, 300 bytes per map.
        int mapCount = Math.toIntExact(max / 10 / 300);
        Map<Object, Map<?,?>> root = smallObjects(new HashMap<>(), mapCount);
        long afterRoot = currentMemoryUsage();
        System.out.println("Root takes up around: " + (afterRoot - begin) + ": " + (afterRoot - begin)*100/max + "%");
        long limit = max * 950/1000; // 95%, but can provoke up to like 99.5%
        long sum = 0;
        for (int forever = 0; forever < 1000000; ++forever) {
            List<byte[]> bytes = new ArrayList<>();
            for (int i = 0; i < 200; ++i) {
                bytes.add(new byte[8000000]);
                long currentMemoryUsage = currentMemoryUsage();
                if (currentMemoryUsage > limit) {
                    LocalDateTime now = LocalDateTime.now();
                    Thread.sleep(5000);
                    long currentMemoryUsageAfterWait = currentMemoryUsage();
                    if (currentMemoryUsageAfterWait > limit) {
                        System.out.println(now + " After wait exceeded limit: " + currentMemoryUsage + "/" + limit + " (" + currentMemoryUsage*10000/max + ")");
                    } else {
                        System.out.println(now + " Before wait only exceeded limit: " + currentMemoryUsage + "/" + limit);
                    }
                    // blackhole
                    sum += mapSum(smallObjects(new HashMap<>(), mapCount));
                }
            }
            // blackhole
            System.out.println("sum: " + bytes.stream().mapToInt(b -> b.length + b[1000]).sum() + ", mem: " + currentMemoryUsage() + "/" + limit);
        }

        // blackhole
        System.out.println("root sum" + mapSum(root) + sum);
    }

    private static int mapSum(Map<Object, Map<?, ?>> root) {
        return root.entrySet().stream().mapToInt(e -> e.getKey().hashCode() + e.getValue().size()).sum();
    }

    private static Map<Object, Map<?, ?>> smallObjects(Map<Object, Map<?, ?>> root, int count) {
        for (int i = 0; i < count; ++i) {
            Map<?,?> oldRoot = root;
            root = new HashMap<>();
            root.put("A" + currentMemoryUsage(), oldRoot);
            root.put("B" + currentMemoryUsage(), root);
        }
        return root;
    }

    static long currentMemoryUsage() {
        return MEMORY_MX_BEAN.getHeapMemoryUsage().getUsed();
    }
}
