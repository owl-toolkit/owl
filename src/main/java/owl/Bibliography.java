/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class Bibliography {

  // Authors (sorted alphabetically)

  private static final String ABU_RADI = "Bader|Abu Radi";
  private static final String BOKER = "Udi Boker";
  private static final String CASARES = "Antonio Casares";
  private static final String COLCOMBET = "Thomas Colcombet";
  private static final String COURCOUBETIS = "Costas Courcoubetis";
  private static final String DURET_LUTZ = "Alexandre Duret-Lutz";
  private static final String ESPARZA = "Javier Esparza";
  private static final String FIJALKOW = "Nathanaël Fijalkow";
  private static final String JAAX = "Stefan Jaax";
  private static final String KRETINSKY = "Jan Křetínský";
  private static final String KUPFERMAN = "Orna Kupferman";
  private static final String LODING = "Christof Löding";
  private static final String LUTTENBERGER = "Michael Luttenberger";
  private static final String MEGGENDORFER = "Tobias Meggendorfer";
  private static final String MEYER = "Klara J. Meyer";
  private static final String MULLER = "David Müller";
  private static final String PIROGOV = "Anton Pirogov";
  private static final String RASKIN = "Jean-François Raskin";
  private static final String RENKIN = "Florian Renkin";
  private static final String SICKERT = "Salomon Sickert";
  private static final String STEINITZ = "Avital Steinitz";
  private static final String WALDMANN = "Clara Waldmann";
  private static final String WEININGER = "Maximilian Weininger";
  private static final String YANNAKAKIS = "Mihalis Yannakakis";
  private static final String ZIEGLER = "Christopher Ziegler";

  // Venues (sorted alphabetically)

  private static final String ATVA = "ATVA";
  private static final String CAV = "CAV";
  private static final String FMSD = "Formal Methods in System Design";
  private static final String FSTTCS = "FSTTCS";
  private static final String GANDALF = "GandALF";
  private static final String ICALP = "ICALP";
  private static final String JACM = "Journal of the ACM";
  private static final String LICS = "LICS";
  private static final String TACAS = "TACAS";
  private static final String TUM = "TUM";

  private static final String UNDER_SUBMISSION = "Under Submission";

  // Publications (sorted chronologically)

  public static final String JACM_95_CITEKEY = "CY95";

  public static final Publication JACM_95 = Publication.of(
    List.of(COURCOUBETIS, YANNAKAKIS),
    "The Complexity of Probabilistic Verification",
    JACM,
    1995,
    "10.1145/210332.210339",
    "DBLP:journals/jacm/CourcoubetisY95"
  );

  public static final String FSTTCS_10_CITEKEY = "BKS10";

  public static final Publication FSTTCS_10 = Publication.of(
    List.of(BOKER, KUPFERMAN, STEINITZ),
    "Parityizing Rabin and Streett",
    FSTTCS,
    2010,
    "10.4230/LIPIcs.FSTTCS.2010.412",
    "DBLP:conf/fsttcs/BokerKS10"
  );

  public static final String CAV_16_CITEKEY = "SEJK16";

  public static final Publication CAV_16 = Publication.of(
    List.of(SICKERT, ESPARZA, JAAX, KRETINSKY),
    "Limit-Deterministic Büchi Automata for Linear Temporal Logic",
    CAV,
    2016,
    "10.1007/978-3-319-41540-6_17",
    "DBLP:conf/cav/SickertEJK16"
  );

  public static final String FMSD_16_CITEKEY = "EKS16";

  public static final Publication FMSD_16 = Publication.of(
    List.of(ESPARZA, KRETINSKY, SICKERT),
    "From LTL to deterministic automata - A safraless compositional approach",
    FMSD,
    2016,
    "10.1007/s10703-016-0259-2",
    "DBLP:journals/fmsd/EsparzaKS16"
  );

  public static final String GANDALF_17_CITEKEY = "MS17";

  public static final Publication GANDALF_17 = Publication.of(
    List.of(MULLER, SICKERT),
    "LTL to Deterministic Emerson-Lei Automata",
    GANDALF,
    2017,
    "10.4204/EPTCS.256.13",
    "DBLP:journals/corr/abs-1709-02102"
  );

  public static final String TACAS_17_1_CITEKEY = "EKRS17";

  public static final Publication TACAS_17_1 = Publication.of(
    List.of(ESPARZA, KRETINSKY, RASKIN, SICKERT),
    "From LTL and Limit-Deterministic Büchi Automata to Deterministic Parity Automata",
    TACAS,
    2017,
    "10.1007/978-3-662-54577-5_25",
    "DBLP:conf/tacas/EsparzaKRS17"
  );

  public static final String TACAS_17_2_CITEKEY = "KMWW17";

  public static final Publication TACAS_17_2 = Publication.of(
    List.of(KRETINSKY, MEGGENDORFER, WALDMANN, WEININGER),
    "Index Appearance Record for Transforming Rabin Automata into Parity Automata",
    TACAS,
    2017,
    "10.1007/978-3-662-54577-5_26",
    "DBLP:conf/tacas/KretinskyMWW17"
  );

  public static final String ATVA_18_CITEKEY = "KMS18";

  public static final Publication ATVA_18 = Publication.of(
    List.of(KRETINSKY, MEGGENDORFER, SICKERT),
    "Owl: A Library for ω-Words, Automata, and LTL",
    ATVA,
    2018,
    "10.1007/978-3-030-01090-4_34",
    "DBLP:conf/atva/KretinskyMS18"
  );

  public static final String CAV_18_CITEKEY = "KMSZ18";

  public static final Publication CAV_18 = Publication.of(
    List.of(KRETINSKY, MEGGENDORFER, SICKERT, ZIEGLER),
    "Rabinizer 4: From LTL to Your Favourite Deterministic Automaton",
    CAV,
    2018,
    "10.1007/978-3-319-96145-3_30",
    "DBLP:conf/cav/KretinskyMSZ18"
  );

  public static final String LICS_18_CITEKEY = "EKS18";

  public static final Publication LICS_18 = Publication.of(
    List.of(ESPARZA, KRETINSKY, SICKERT),
    "One Theorem to Rule Them All: A Unified Translation of LTL into ω-Automata",
    LICS,
    2018,
    "10.1145/3209108.3209161",
    "DBLP:conf/lics/EsparzaKS18"
  );

  public static final String ATVA_19_CITEKEY = "LP19a";

  public static final Publication ATVA_19 = Publication.of(
    List.of(LODING, PIROGOV),
    "New Optimizations and Heuristics for Determinization of Büchi",
    ATVA,
    2019,
    "10.1007/978-3-030-31784-3_18",
    "DBLP:conf/atva/LodingP19"
  );

  public static final String DISSERTATION_19_CITEKEY = "S19";

  public static final Publication DISSERTATION_19 = Publication.of(
    List.of(SICKERT),
    "A Unified Translation of Linear Temporal Logic to ω-Automata",
    TUM,
    2019,
    null,
    "DBLP:phd/dnb/Sickert19"
  );

  public static final String ICALP_19_1_CITEKEY = "LP19b";

  public static final Publication ICALP_19_1 = Publication.of(
    List.of(LODING, PIROGOV),
    "Determinization of Büchi Automata: Unifying the Approaches of Safra and Muller-Schupp",
    ICALP,
    2019,
    "10.4230/LIPIcs.ICALP.2019.120",
    "DBLP:conf/icalp/LodingP19"
  );

  public static final String ICALP_19_2_CITEKEY = "AK19";

  public static final Publication ICALP_19_2 = Publication.of(
    List.of(ABU_RADI, KUPFERMAN),
    "Minimizing GFG Transition-Based Automata",
    ICALP,
    2019,
    "10.4230/LIPIcs.ICALP.2019.100",
    "DBLP:conf/icalp/RadiK19"
  );

  public static final String LICS_20_CITEKEY = "SE20";

  public static final Publication LICS_20 = Publication.of(
    List.of(SICKERT, ESPARZA),
    "An Efficient Normalisation Procedure for Linear Temporal Logic and Very Weak Alternating "
      + "Automata",
    LICS,
    2020,
    "10.1145/3373718.3394743",
    "DBLP:conf/lics/SickertE20"
  );

  public static final String JACM_20_CITEKEY = "EKS20";

  public static final Publication JACM_20 = Publication.of(
    List.of(ESPARZA, KRETINSKY, SICKERT),
    "A Unified Translation of Linear Temporal Logic to ω-Automata",
    JACM,
    2020,
    "10.1145/3417995",
    "DBLP:journals/jacm/EsparzaKS20"
  );

  public static final String ICALP_21_CITEKEY = "CCF21";

  public static final Publication ICALP_21 = Publication.of(
    List.of(CASARES, COLCOMBET, FIJALKOW),
    "Optimal Transformations of Games and Automata Using Muller Conditions",
    ICALP,
    2021,
    "10.4230/LIPIcs.ICALP.2021.123",
    "DBLP:conf/icalp/CasaresCF21"
  );

  public static final String UNDER_SUBMISSION_21_CITEKEY = "SLM21";

  public static final Publication UNDER_SUBMISSION_21 = Publication.of(
    List.of(SICKERT, LUTTENBERGER, MEYER),
    "(title not known yet)",
    UNDER_SUBMISSION,
    2021,
    null,
    null
  );

  public static final String UNDER_SUBMISSION_22_CITEKEY = "CDMRS22";

  public static final Publication UNDER_SUBMISSION_22 = Publication.of(
    List.of(CASARES, DURET_LUTZ, MEYER, RENKIN, SICKERT),
    "Practical Applications of the Alternating Cycle Decomposition",
    UNDER_SUBMISSION,
    2022,
    null,
    null
  );

  // This field is initialised with values obtained through reflection.
  public static final Map<String, Publication> INDEX;

  static {
    Map<String, List<Publication>> index = new HashMap<>();
    Map<Publication, String> predefinedCiteKeys = new HashMap<>();

    try {
      Field[] declaredFields = Bibliography.class.getDeclaredFields();

      for (Field field : declaredFields) {
        if (Modifier.isStatic(field.getModifiers())) {
          Object object = field.get(null);

          if (object instanceof Publication publication) {
            var predefinedCiteKey = Bibliography.class.getField(field.getName() + "_CITEKEY");

            index.merge(publication.citeKey(), List.of(publication), (x, y) -> {
              var xCopy = new ArrayList<>(x);
              xCopy.addAll(y);
              return xCopy;
            });

            predefinedCiteKeys.put(publication, (String) predefinedCiteKey.get(null));
          }
        }
      }
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }

    Map<String, Publication> disambiguatedIndex = new HashMap<>();

    index.forEach((key, publications) -> {
      if (publications.size() == 1) {
        disambiguatedIndex.put(key, publications.get(0));
        return;
      }

      publications.sort(Comparator.comparing(Publication::venue));

      char suffix = 'a';

      for (Publication publication : publications) {
        var oldValue = disambiguatedIndex.put(key + suffix, publication);
        assert oldValue == null;
        suffix++;
      }
    });

    disambiguatedIndex.forEach((key, publication) -> {
      assert key.equals(predefinedCiteKeys.get(publication));
    });

    INDEX = Map.copyOf(disambiguatedIndex);
  }

  private Bibliography() {}

  @AutoValue
  public abstract static class Publication {

    public abstract List<String> authors();

    public abstract String title();

    public abstract String venue();

    public abstract int year();

    public abstract Optional<String> doi();

    public abstract Optional<String> dblpKey();

    private static Publication of(List<String> authors, String title, String venue, int year,
      @Nullable String doi, @Nullable String dblpKey) {

      Preconditions.checkArgument(dblpKey == null || dblpKey.startsWith("DBLP:"));

      return new AutoValue_Bibliography_Publication(
        List.copyOf(authors),
        title,
        venue,
        year,
        Optional.ofNullable(doi),
        Optional.ofNullable(dblpKey));
    }

    @Override
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public final String toString() {
      var publication = String.format("%s:\n\"%s\". %s %s\n",
        String.join(", ", authors()).replace('|', ' '),
        title(),
        venue(),
        year()
      );

      if (doi().isPresent()) {
        publication += String.format("DOI: https://doi.org/%s\n", doi().get());
      }

      if (dblpKey().isPresent()) {
        publication += String.format(
          "BibTeX: https://dblp.uni-trier.de/rec/bibtex/%s\n",
          dblpKey().get().substring(5));
      }

      return publication;
    }

    private String citeKey() {
      return authors().stream()
        .map(x -> String.valueOf(
          x.charAt(x.contains("|") ? x.indexOf('|') + 1 : x.lastIndexOf(' ') + 1)))
        .collect(Collectors.joining())
        + Integer.toString(year()).substring(2);
    }
  }
}
