package local.grworld.studio;

import java.io.IOException;
import java.util.*;

/** Data-only module/effect choices. Camera settings are chosen in GRK after loading. */
final class EnhancementSelection {
  final boolean framing, guides, film, date;
  final SortedSet<String> effects;
  boolean manageColor = true;

  EnhancementSelection(
      boolean framing, boolean guides, boolean film, boolean date, Collection<String> effects)
      throws IOException {
    this.framing = framing;
    this.guides = guides;
    this.film = film;
    this.date = date;
    TreeSet<String> names = new TreeSet<>();
    for (String name : effects) {
      if (!name.matches("[A-F0-9]{8}\\.GRF") || !names.add(name))
        throw new IOException("滤镜文件名重复或无效");
    }
    if (names.size() > 40) throw new IOException("最多选择 40 个胶片风格");
    this.effects = Collections.unmodifiableSortedSet(names);
  }

  static EnhancementSelection basic(boolean f, boolean g) throws IOException {
    EnhancementSelection choice =
        new EnhancementSelection(f, g, false, false, Collections.emptyList());
    choice.manageColor = false;
    return choice;
  }

  Set<String> modules() {
    Set<String> names = new LinkedHashSet<>();
    if (framing) names.add("FRAMING.GRM");
    if (guides) names.add("GUIDES.GRM");
    if (film) names.add("FILMLOOK.GRM");
    if (date) names.add("DATE.GRM");
    return names;
  }

  String description() {
    List<String> names = new ArrayList<>();
    if (framing) names.add("XPan 宽幅");
    if (guides) names.add("构图辅助线");
    if (film) names.add("胶片模拟（" + effects.size() + " 个风格）");
    if (date) names.add("日期打印");
    return String.join("、", names);
  }
}
