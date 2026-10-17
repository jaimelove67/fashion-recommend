package com.fashion.recommendation.trend;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Daily editorial slots, independent of an author's gender, popularity or follower tier. */
public final class TrendDailySelection {
    private TrendDailySelection() {}

    private static final Pattern MENSWEAR = Pattern.compile("男(?:装|士|生)(?:穿搭|搭配|服装|造型)|\\bmenswear\\b|\\bmen['’]s outfits?\\b");
    private static final Pattern WOMENSWEAR = Pattern.compile("女(?:装|士|生)(?:穿搭|搭配|服装|造型)|\\bwomenswear\\b|\\bwomen['’]s outfits?\\b");
    private static final Pattern UNISEX = Pattern.compile("男女(?:同款|通穿|都能穿|皆宜)|无性别(?:穿搭|服装|时装)|男女装");
    private static final Pattern NICHE_STYLE = Pattern.compile("哥特(?:风|式)?|洛丽塔|蒸汽朋克|赛博朋克|视觉系|解构(?:风|主义|穿搭)|暗黑先锋");
    private static final Pattern NICHE_LABEL = Pattern.compile("小众穿搭|小众风格");
    private static final Pattern NON_APPAREL_STYLE_SUFFIX = Pattern.compile(
            "^\\s*(?:式)?(?:的)?(?:建筑|教堂|电影|游戏|小说|家居|家具|装修|场景|背景|[0-9]{2,4}游戏)");
    private static final Pattern APPAREL = Pattern.compile("穿搭|搭配|服装|时装|造型|\\bootd\\b|\\boutfits?\\b");
    private static final Pattern NEGATED_PREFIX = Pattern.compile("(?:不是|并非|非|不走|不做|不属于|告别|拒绝|没有|远离|不再是|不再走)\\s*$");
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "spm", "from", "from_source", "share_source", "share_medium", "share_token",
            "xsec_token", "xsec_source", "featurecode", "wm");
    private static final Pattern SINA_HOST = Pattern.compile("(?:wx|ws|ww)[0-9]+\\.sinaimg\\.(?:cn|com)");
    private static final Pattern SINA_SIZE = Pattern.compile(
            "^/(?:large|middle|bmiddle|thumbnail|small|square|woriginal|mw[0-9]+|orj[0-9]+|thumb[0-9]+)/");

    /** Candidates must already satisfy the caller's freshness, image and content-review policy. */
    public static Result select(List<TrendItem> rankedCandidates) {
        List<Candidate> candidates = new ArrayList<>();
        if (rankedCandidates != null) for (int rank = 0; rank < rankedCandidates.size(); rank++) {
            TrendItem item = rankedCandidates.get(rank);
            if (item == null) continue;
            Classification classification = classify(item);
            if (classification != null) candidates.add(new Candidate(item, classification, identities(item)));
        }
        List<Candidate> selected = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("menswear", 0);
        counts.put("womenswear", 0);
        counts.put("niche", 0);
        for (int index : bestSelection(candidates)) {
            Candidate candidate = candidates.get(index);
            String category = candidate.classification().category();
            selected.add(candidate);
            counts.compute(category, (key, count) -> count + 1);
        }
        List<TrendItem> items = selected.stream().map(Candidate::item).toList();
        List<Assignment> assignments = selected.stream().map(candidate -> new Assignment(candidate.item().id(),
                candidate.classification().category(), candidate.classification().label(), candidate.classification().reason())).toList();
        Map<String, Integer> missing = new LinkedHashMap<>();
        missing.put("menswear", 2 - counts.get("menswear"));
        missing.put("womenswear", 2 - counts.get("womenswear"));
        missing.put("niche", 1 - counts.get("niche"));
        return new Result(List.copyOf(items), new Summary(5, counts.get("menswear"), counts.get("womenswear"),
                counts.get("niche"), java.util.Collections.unmodifiableMap(missing), List.copyOf(assignments)));
    }

    /** Maximise filled slots, then prefer the earliest original ranks lexicographically. */
    private static int[] bestSelection(List<Candidate> candidates) {
        BitSet men = new BitSet(candidates.size());
        BitSet women = new BitSet(candidates.size());
        BitSet niche = new BitSet(candidates.size());
        BitSet[] compatible = new BitSet[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            switch (candidates.get(index).classification().category()) {
                case "menswear" -> men.set(index);
                case "womenswear" -> women.set(index);
                case "niche" -> niche.set(index);
                default -> throw new IllegalStateException("Unknown daily outfit category");
            }
            compatible[index] = new BitSet(candidates.size());
            for (int other = 0; other < index; other++) {
                if (Collections.disjoint(candidates.get(index).identities(), candidates.get(other).identities())) {
                    compatible[index].set(other);
                    compatible[other].set(index);
                }
            }
        }
        // A five-item subset search can grow as n^5. Instead enumerate at most one niche
        // candidate and a men's pair, then find the earliest compatible women's pair with
        // bit operations. For a full set, 150 candidates require under 250,000 base triples.
        for (int target = 5; target > 0; target--) {
            int[] best = null;
            for (int menCount = 0; menCount <= 2; menCount++) {
                for (int womenCount = 0; womenCount <= 2; womenCount++) {
                    int nicheCount = target - menCount - womenCount;
                    if (nicheCount < 0 || nicheCount > 1 || men.cardinality() < menCount
                            || women.cardinality() < womenCount || niche.cardinality() < nicheCount) continue;
                    int[] nicheChoices = nicheCount == 0 ? new int[] {-1} : niche.stream().toArray();
                    for (int nicheIndex : nicheChoices) {
                        int[] base = nicheIndex < 0 ? new int[0] : new int[] {nicheIndex};
                        BitSet allowedMen = restrict(men, base, compatible);
                        BitSet allowedWomen = restrict(women, base, compatible);
                        if (allowedMen.cardinality() < menCount || allowedWomen.cardinality() < womenCount) continue;
                        if (menCount == 0) {
                            best = consider(base, allowedWomen, womenCount, compatible, best);
                            continue;
                        }
                        for (int first = allowedMen.nextSetBit(0); first >= 0; first = allowedMen.nextSetBit(first + 1)) {
                            if (menCount == 1) {
                                best = consider(append(base, first), allowedWomen, womenCount, compatible, best);
                                continue;
                            }
                            BitSet seconds = restrict(allowedMen, new int[] {first}, compatible);
                            for (int second = seconds.nextSetBit(first + 1); second >= 0; second = seconds.nextSetBit(second + 1)) {
                                best = consider(append(base, first, second), allowedWomen, womenCount, compatible, best);
                            }
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return new int[0];
    }

    private static int[] consider(int[] base, BitSet women, int womenCount, BitSet[] compatible, int[] best) {
        BitSet allowed = restrict(women, base, compatible);
        if (allowed.cardinality() < womenCount) return best;
        int[] chosen = base;
        if (womenCount == 1) chosen = append(base, allowed.nextSetBit(0));
        if (womenCount == 2) {
            chosen = null;
            for (int first = allowed.nextSetBit(0); first >= 0; first = allowed.nextSetBit(first + 1)) {
                BitSet seconds = restrict(allowed, new int[] {first}, compatible);
                int second = seconds.nextSetBit(first + 1);
                if (second >= 0) {
                    chosen = append(base, first, second);
                    break;
                }
            }
            if (chosen == null) return best;
        }
        Arrays.sort(chosen);
        if (best == null) return chosen;
        for (int index = 0; index < chosen.length; index++) {
            if (chosen[index] < best[index]) return chosen;
            if (chosen[index] > best[index]) return best;
        }
        return best;
    }

    private static BitSet restrict(BitSet candidates, int[] selected, BitSet[] compatible) {
        BitSet allowed = (BitSet) candidates.clone();
        for (int index : selected) allowed.and(compatible[index]);
        return allowed;
    }

    private static int[] append(int[] base, int... additions) {
        int[] result = Arrays.copyOf(base, base.length + additions.length);
        System.arraycopy(additions, 0, result, base.length, additions.length);
        return result;
    }

    private static Classification classify(TrendItem item) {
        // Only original title and explicit topic labels are classification evidence. An author name,
        // follower count or generated summary must never infer the clothes' intended category.
        List<String> evidence = new ArrayList<>();
        if (item.title() != null) evidence.add(item.title().toLowerCase(Locale.ROOT));
        if (item.topicTags() != null) item.topicTags().stream().filter(tag -> tag != null)
                .map(tag -> tag.toLowerCase(Locale.ROOT)).forEach(evidence::add);
        boolean apparel = evidence.stream().anyMatch(text -> positiveMatch(APPAREL, text) != null);
        String nicheStyle = evidence.stream().map(TrendDailySelection::positiveNicheStyle)
                .filter(style -> style != null).findFirst().orElse(null);
        if (apparel && nicheStyle != null) return new Classification("niche", "小众穿搭欣赏",
                "原始标题或标签明确标注“" + nicheStyle + "”服装风格；单独占用小众欣赏名额");
        // Explicit niche labelling without a verifiable style remains unfilled rather than being
        // silently repurposed as one of the four everyday outfit slots.
        if (evidence.stream().anyMatch(text -> positiveMatch(NICHE_LABEL, text) != null)) return null;
        if (evidence.stream().anyMatch(text -> positiveMatch(UNISEX, text) != null)) return null;
        boolean men = evidence.stream().anyMatch(text -> positiveMatch(MENSWEAR, text) != null);
        boolean women = evidence.stream().anyMatch(text -> positiveMatch(WOMENSWEAR, text) != null);
        if (men == women) return null;
        return men ? new Classification("menswear", "男装穿搭", "原始标题或标签明确标注男装穿搭")
                : new Classification("womenswear", "女装穿搭", "原始标题或标签明确标注女装穿搭");
    }

    private static String positiveMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String prefix = text.substring(Math.max(0, matcher.start() - 8), matcher.start());
            if (!NEGATED_PREFIX.matcher(prefix).find()) return matcher.group();
        }
        return null;
    }

    private static String positiveNicheStyle(String text) {
        // A standalone collector label is explicit; prose containing the verb 解构 is not.
        if ("解构".equals(text.strip())) return "解构";
        Matcher matcher = NICHE_STYLE.matcher(text);
        while (matcher.find()) {
            String prefix = text.substring(Math.max(0, matcher.start() - 8), matcher.start());
            String suffix = text.substring(matcher.end());
            if (!NEGATED_PREFIX.matcher(prefix).find() && !NON_APPAREL_STYLE_SUFFIX.matcher(suffix).find())
                return matcher.group();
        }
        return null;
    }

    private static Set<String> identities(TrendItem item) {
        Set<String> identities = new HashSet<>();
        if (item.id() != null && !item.id().isBlank()) identities.add("id:" + item.id().strip());
        String source = canonicalUrl(item.sourceUrl(), false);
        if (source != null) identities.add("source:" + source);
        // A mixed photo post may share a café or scenery photo with another outfit. Only the
        // verified full-body image, or otherwise the displayed cover, identifies the outfit.
        String fullBody = item.evidence() == null ? null : item.evidence().fullBodyImageUrl();
        String image = canonicalUrl(fullBody, true);
        if (image == null) image = canonicalUrl(item.imageUrl(), true);
        if (image != null) identities.add("image:" + image);
        return identities;
    }

    private static String canonicalUrl(String value, boolean image) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.strip());
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            boolean sinaImage = image && SINA_HOST.matcher(host).matches();
            if (sinaImage) {
                host = "sinaimg.cn";
                path = SINA_SIZE.matcher(path).replaceFirst("/");
            }
            int port = uri.getPort();
            String authority = host + ((port < 0 || port == 80 || port == 443) ? "" : ":" + port);
            // Sina identifies a photo in its path. Other hosts may select different images with
            // ?id=, ?key= or ?url=, so their identity-bearing query parameters must survive.
            String query = sinaImage ? null : uri.getRawQuery();
            if (query != null) query = String.join("&", Arrays.stream(query.split("&"))
                    .filter(parameter -> !isTrackingParameter(parameter)).sorted().toList());
            return authority + path + (query != null && !query.isEmpty() ? "?" + query : "");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isTrackingParameter(String parameter) {
        String key = URLDecoder.decode(parameter.split("=", 2)[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return key.startsWith("utm_") || TRACKING_PARAMETERS.contains(key);
    }

    private record Classification(String category, String label, String reason) {}
    private record Candidate(TrendItem item, Classification classification, Set<String> identities) {}
    public record Result(List<TrendItem> items, Summary summary) {}
    public record Summary(int target, int menswear, int womenswear, int niche,
            Map<String, Integer> missing, List<Assignment> assignments) {}
    public record Assignment(String id, String category, String label, String reason) {}
}
