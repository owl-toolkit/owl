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

package owl.command;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.thirdparty.picocli.CommandLine.ArgGroup;
import static owl.thirdparty.picocli.CommandLine.Command;
import static owl.thirdparty.picocli.CommandLine.Mixin;
import static owl.thirdparty.picocli.CommandLine.Option;
import static owl.thirdparty.picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import owl.Bibliography;
import owl.automaton.Automaton;
import owl.automaton.ParityUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.hoa.HoaWriter;
import owl.game.GameViews;
import owl.game.PgSolverFormat;
import owl.ltl.SyntacticFragments;

@SuppressWarnings({"PMD.ImmutableField", "PMD.SystemPrintln"})
final class MiscCommands {

  private MiscCommands() {}

  @Command(
    name = "bibliography",
    description = "Print the bibliography of all implemented algorithms and constructions. "
      + "Single references can be looked up by listing them, e.g. 'owl bibliography SE20'. "
      + "If you want to cite Owl as a whole, it is recommended to use reference [KMS18]."
  )
  static final class BibliographyCommand extends AbstractOwlSubcommand {

    static final String HOW_TO_USE = "To look up a reference, e.g. [SE20], used in this help "
      + "message please use 'owl bibliography'.";

    @Parameters(
      description = "Print the bibliography only for the specified references."
    )
    @Nullable
    private String[] references;

    @Override
    protected int run() throws Exception {
      if (references == null) {
        // Sort entries by their citeKey.
        new TreeMap<>(Bibliography.INDEX).forEach(BibliographyCommand::printEntry);
      } else {
        for (var citeKey : references) {
          printEntry(citeKey, Bibliography.INDEX.get(citeKey));
        }
      }

      return 0;
    }

    private static void printEntry(String citeKey, Bibliography.Publication publication) {
      if (publication == null) {
        System.err.printf("[%s]:%nNo entry found.%n%n", citeKey);
        return;
      }

      System.out.printf("[%s]:%n%s%n", citeKey, publication);
    }
  }

  @Command(
    name = "license",
    description = "Print the license of Owl and the licenses of all linked (non-system) libraries."
  )
  static final class LicenseCommand extends AbstractOwlSubcommand {

    private static final String ANTLR_4_LIBRARY =
      "antlr4(-runtime)-4.8-1.jar (Copyright (C) 2012 Terence Parr and Sam Harwell)";

    private static final String ANTLR_4_LICENSE = """
      [The BSD License]
      Copyright (c) 2012 Terence Parr and Sam Harwell
      All rights reserved.

      Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

          Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
          Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
          Neither the name of the author nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.\s

      THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.""";

    private static final String GRAAL_LIBRARY =
      "graal-sdk-21.0.0.2.jar (Copyright 2021 (C) Oracle and/or its affiliates)";

    private static final String GUAVA_LIBRARY =
      "guava-30.1-jre.jar (Copyright 2021 (C) The Guava Authors)";

    private static final String JBDD_LIBRARY =
      "jbdd-0.5.2.jar (Copyright 2017 (C) Tobias Meggendorfer and contributors)";

    private static final String JHOAFPARSER_LIBRARY =
      "jhoafparser-1.1.1 (Copyright 2014-2015 (C) Joachim Klein, David Müller)";

    private static final String PICOCLI_LIBRARY =
      "picocli-4.6.1.jar (Copyright 2017-2021 (C) Remko Popma)";

    // Standard Licenses.

    private static final String APACHE_2_LICENSE = """
                                    Apache License
                              Version 2.0, January 2004
                           http://www.apache.org/licenses/

      TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

      1. Definitions.

         "License" shall mean the terms and conditions for use, reproduction,
         and distribution as defined by Sections 1 through 9 of this document.

         "Licensor" shall mean the copyright owner or entity authorized by
         the copyright owner that is granting the License.

         "Legal Entity" shall mean the union of the acting entity and all
         other entities that control, are controlled by, or are under common
         control with that entity. For the purposes of this definition,
         "control" means (i) the power, direct or indirect, to cause the
         direction or management of such entity, whether by contract or
         otherwise, or (ii) ownership of fifty percent (50%) or more of the
         outstanding shares, or (iii) beneficial ownership of such entity.

         "You" (or "Your") shall mean an individual or Legal Entity
         exercising permissions granted by this License.

         "Source" form shall mean the preferred form for making modifications,
         including but not limited to software source code, documentation
         source, and configuration files.

         "Object" form shall mean any form resulting from mechanical
         transformation or translation of a Source form, including but
         not limited to compiled object code, generated documentation,
         and conversions to other media types.

         "Work" shall mean the work of authorship, whether in Source or
         Object form, made available under the License, as indicated by a
         copyright notice that is included in or attached to the work
         (an example is provided in the Appendix below).

         "Derivative Works" shall mean any work, whether in Source or Object
         form, that is based on (or derived from) the Work and for which the
         editorial revisions, annotations, elaborations, or other modifications
         represent, as a whole, an original work of authorship. For the purposes
         of this License, Derivative Works shall not include works that remain
         separable from, or merely link (or bind by name) to the interfaces of,
         the Work and Derivative Works thereof.

         "Contribution" shall mean any work of authorship, including
         the original version of the Work and any modifications or additions
         to that Work or Derivative Works thereof, that is intentionally
         submitted to Licensor for inclusion in the Work by the copyright owner
         or by an individual or Legal Entity authorized to submit on behalf of
         the copyright owner. For the purposes of this definition, "submitted"
         means any form of electronic, verbal, or written communication sent
         to the Licensor or its representatives, including but not limited to
         communication on electronic mailing lists, source code control systems,
         and issue tracking systems that are managed by, or on behalf of, the
         Licensor for the purpose of discussing and improving the Work, but
         excluding communication that is conspicuously marked or otherwise
         designated in writing by the copyright owner as "Not a Contribution."

         "Contributor" shall mean Licensor and any individual or Legal Entity
         on behalf of whom a Contribution has been received by Licensor and
         subsequently incorporated within the Work.

      2. Grant of Copyright License. Subject to the terms and conditions of
         this License, each Contributor hereby grants to You a perpetual,
         worldwide, non-exclusive, no-charge, royalty-free, irrevocable
         copyright license to reproduce, prepare Derivative Works of,
         publicly display, publicly perform, sublicense, and distribute the
         Work and such Derivative Works in Source or Object form.

      3. Grant of Patent License. Subject to the terms and conditions of
         this License, each Contributor hereby grants to You a perpetual,
         worldwide, non-exclusive, no-charge, royalty-free, irrevocable
         (except as stated in this section) patent license to make, have made,
         use, offer to sell, sell, import, and otherwise transfer the Work,
         where such license applies only to those patent claims licensable
         by such Contributor that are necessarily infringed by their
         Contribution(s) alone or by combination of their Contribution(s)
         with the Work to which such Contribution(s) was submitted. If You
         institute patent litigation against any entity (including a
         cross-claim or counterclaim in a lawsuit) alleging that the Work
         or a Contribution incorporated within the Work constitutes direct
         or contributory patent infringement, then any patent licenses
         granted to You under this License for that Work shall terminate
         as of the date such litigation is filed.

      4. Redistribution. You may reproduce and distribute copies of the
         Work or Derivative Works thereof in any medium, with or without
         modifications, and in Source or Object form, provided that You
         meet the following conditions:

         (a) You must give any other recipients of the Work or
             Derivative Works a copy of this License; and

         (b) You must cause any modified files to carry prominent notices
             stating that You changed the files; and

         (c) You must retain, in the Source form of any Derivative Works
             that You distribute, all copyright, patent, trademark, and
             attribution notices from the Source form of the Work,
             excluding those notices that do not pertain to any part of
             the Derivative Works; and

         (d) If the Work includes a "NOTICE" text file as part of its
             distribution, then any Derivative Works that You distribute must
             include a readable copy of the attribution notices contained
             within such NOTICE file, excluding those notices that do not
             pertain to any part of the Derivative Works, in at least one
             of the following places: within a NOTICE text file distributed
             as part of the Derivative Works; within the Source form or
             documentation, if provided along with the Derivative Works; or,
             within a display generated by the Derivative Works, if and
             wherever such third-party notices normally appear. The contents
             of the NOTICE file are for informational purposes only and
             do not modify the License. You may add Your own attribution
             notices within Derivative Works that You distribute, alongside
             or as an addendum to the NOTICE text from the Work, provided
             that such additional attribution notices cannot be construed
             as modifying the License.

         You may add Your own copyright statement to Your modifications and
         may provide additional or different license terms and conditions
         for use, reproduction, or distribution of Your modifications, or
         for any such Derivative Works as a whole, provided Your use,
         reproduction, and distribution of the Work otherwise complies with
         the conditions stated in this License.

      5. Submission of Contributions. Unless You explicitly state otherwise,
         any Contribution intentionally submitted for inclusion in the Work
         by You to the Licensor shall be under the terms and conditions of
         this License, without any additional terms or conditions.
         Notwithstanding the above, nothing herein shall supersede or modify
         the terms of any separate license agreement you may have executed
         with Licensor regarding such Contributions.

      6. Trademarks. This License does not grant permission to use the trade
         names, trademarks, service marks, or product names of the Licensor,
         except as required for reasonable and customary use in describing the
         origin of the Work and reproducing the content of the NOTICE file.

      7. Disclaimer of Warranty. Unless required by applicable law or
         agreed to in writing, Licensor provides the Work (and each
         Contributor provides its Contributions) on an "AS IS" BASIS,
         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
         implied, including, without limitation, any warranties or conditions
         of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
         PARTICULAR PURPOSE. You are solely responsible for determining the
         appropriateness of using or redistributing the Work and assume any
         risks associated with Your exercise of permissions under this License.

      8. Limitation of Liability. In no event and under no legal theory,
         whether in tort (including negligence), contract, or otherwise,
         unless required by applicable law (such as deliberate and grossly
         negligent acts) or agreed to in writing, shall any Contributor be
         liable to You for damages, including any direct, indirect, special,
         incidental, or consequential damages of any character arising as a
         result of this License or out of the use or inability to use the
         Work (including but not limited to damages for loss of goodwill,
         work stoppage, computer failure or malfunction, or any and all
         other commercial damages or losses), even if such Contributor
         has been advised of the possibility of such damages.

      9. Accepting Warranty or Additional Liability. While redistributing
         the Work or Derivative Works thereof, You may choose to offer,
         and charge a fee for, acceptance of support, warranty, indemnity,
         or other liability obligations and/or rights consistent with this
         License. However, in accepting such obligations, You may act only
         on Your own behalf and on Your sole responsibility, not on behalf
         of any other Contributor, and only if You agree to indemnify,
         defend, and hold each Contributor harmless for any liability
         incurred by, or claims asserted against, such Contributor by reason
         of your accepting any such warranty or additional liability.

      END OF TERMS AND CONDITIONS

      APPENDIX: How to apply the Apache License to your work.

         To apply the Apache License to your work, attach the following
         boilerplate notice, with the fields enclosed by brackets "{}"
         replaced with your own identifying information. (Don't include
         the brackets!)  The text should be enclosed in the appropriate
         comment syntax for the file format. We also recommend that a
         file or class name and description of purpose be included on the
         same "printed page" as the copyright notice for easier
         identification within third-party archives.

      Copyright {yyyy} {name of copyright owner}

      Licensed under the Apache License, Version 2.0 (the "License");
      you may not use this file except in compliance with the License.
      You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

      Unless required by applicable law or agreed to in writing, software
      distributed under the License is distributed on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      See the License for the specific language governing permissions and
      limitations under the License.""".indent(3);

    private static final String GPL_3_LICENSE = """
                            GNU GENERAL PUBLIC LICENSE
                             Version 3,  29 June 2007

       Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
       Everyone is permitted to copy and distribute verbatim copies
       of this license document, but changing it is not allowed.

                                  Preamble

        The GNU General Public License is a free, copyleft license for
      software and other kinds of works.

        The licenses for most software and other practical works are designed
      to take away your freedom to share and change the works.  By contrast,
      the GNU General Public License is intended to guarantee your freedom to
      share and change all versions of a program--to make sure it remains free
      software for all its users.  We, the Free Software Foundation, use the
      GNU General Public License for most of our software; it applies also to
      any other work released this way by its authors.  You can apply it to
      your programs, too.

        When we speak of free software, we are referring to freedom, not
      price.  Our General Public Licenses are designed to make sure that you
      have the freedom to distribute copies of free software (and charge for
      them if you wish), that you receive source code or can get it if you
      want it, that you can change the software or use pieces of it in new
      free programs, and that you know you can do these things.

        To protect your rights, we need to prevent others from denying you
      these rights or asking you to surrender the rights.  Therefore, you have
      certain responsibilities if you distribute copies of the software, or if
      you modify it: responsibilities to respect the freedom of others.

        For example, if you distribute copies of such a program, whether
      gratis or for a fee, you must pass on to the recipients the same
      freedoms that you received.  You must make sure that they, too, receive
      or can get the source code.  And you must show them these terms so they
      know their rights.

        Developers that use the GNU GPL protect your rights with two steps:
      (1) assert copyright on the software, and (2) offer you this License
      giving you legal permission to copy, distribute and/or modify it.

        For the developers' and authors' protection, the GPL clearly explains
      that there is no warranty for this free software.  For both users' and
      authors' sake, the GPL requires that modified versions be marked as
      changed, so that their problems will not be attributed erroneously to
      authors of previous versions.

        Some devices are designed to deny users access to install or run
      modified versions of the software inside them, although the manufacturer
      can do so.  This is fundamentally incompatible with the aim of
      protecting users' freedom to change the software.  The systematic
      pattern of such abuse occurs in the area of products for individuals to
      use, which is precisely where it is most unacceptable.  Therefore, we
      have designed this version of the GPL to prohibit the practice for those
      products.  If such problems arise substantially in other domains, we
      stand ready to extend this provision to those domains in future versions
      of the GPL, as needed to protect the freedom of users.

        Finally, every program is threatened constantly by software patents.
      States should not allow patents to restrict development and use of
      software on general-purpose computers, but in those that do, we wish to
      avoid the special danger that patents applied to a free program could
      make it effectively proprietary.  To prevent this, the GPL assures that
      patents cannot be used to render the program non-free.

        The precise terms and conditions for copying, distribution and
      modification follow.

                             TERMS AND CONDITIONS

        0. Definitions.

        "This License" refers to version 3 of the GNU General Public License.

        "Copyright" also means copyright-like laws that apply to other kinds of
      works, such as semiconductor masks.

        "The Program" refers to any copyrightable work licensed under this
      License.  Each licensee is addressed as "you".  "Licensees" and
      "recipients" may be individuals or organizations.

        To "modify" a work means to copy from or adapt all or part of the work
      in a fashion requiring copyright permission, other than the making of an
      exact copy.  The resulting work is called a "modified version" of the
      earlier work or a work "based on" the earlier work.

        A "covered work" means either the unmodified Program or a work based
      on the Program.

        To "propagate" a work means to do anything with it that, without
      permission, would make you directly or secondarily liable for
      infringement under applicable copyright law, except executing it on a
      computer or modifying a private copy.  Propagation includes copying,
      distribution (with or without modification), making available to the
      public, and in some countries other activities as well.

        To "convey" a work means any kind of propagation that enables other
      parties to make or receive copies.  Mere interaction with a user through
      a computer network, with no transfer of a copy, is not conveying.

        An interactive user interface displays "Appropriate Legal Notices"
      to the extent that it includes a convenient and prominently visible
      feature that (1) displays an appropriate copyright notice, and (2)
      tells the user that there is no warranty for the work (except to the
      extent that warranties are provided), that licensees may convey the
      work under this License, and how to view a copy of this License.  If
      the interface presents a list of user commands or options, such as a
      menu, a prominent item in the list meets this criterion.

        1. Source Code.

        The "source code" for a work means the preferred form of the work
      for making modifications to it.  "Object code" means any non-source
      form of a work.

        A "Standard Interface" means an interface that either is an official
      standard defined by a recognized standards body, or, in the case of
      interfaces specified for a particular programming language, one that
      is widely used among developers working in that language.

        The "System Libraries" of an executable work include anything, other
      than the work as a whole, that (a) is included in the normal form of
      packaging a Major Component, but which is not part of that Major
      Component, and (b) serves only to enable use of the work with that
      Major Component, or to implement a Standard Interface for which an
      implementation is available to the public in source code form.  A
      "Major Component", in this context, means a major essential component
      (kernel, window system, and so on) of the specific operating system
      (if any) on which the executable work runs, or a compiler used to
      produce the work, or an object code interpreter used to run it.

        The "Corresponding Source" for a work in object code form means all
      the source code needed to generate, install, and (for an executable
      work) run the object code and to modify the work, including scripts to
      control those activities.  However, it does not include the work's
      System Libraries, or general-purpose tools or generally available free
      programs which are used unmodified in performing those activities but
      which are not part of the work.  For example, Corresponding Source
      includes interface definition files associated with source files for
      the work, and the source code for shared libraries and dynamically
      linked subprograms that the work is specifically designed to require,
      such as by intimate data communication or control flow between those
      subprograms and other parts of the work.

        The Corresponding Source need not include anything that users
      can regenerate automatically from other parts of the Corresponding
      Source.

        The Corresponding Source for a work in source code form is that
      same work.

        2. Basic Permissions.

        All rights granted under this License are granted for the term of
      copyright on the Program, and are irrevocable provided the stated
      conditions are met.  This License explicitly affirms your unlimited
      permission to run the unmodified Program.  The output from running a
      covered work is covered by this License only if the output, given its
      content, constitutes a covered work.  This License acknowledges your
      rights of fair use or other equivalent, as provided by copyright law.

        You may make, run and propagate covered works that you do not
      convey, without conditions so long as your license otherwise remains
      in force.  You may convey covered works to others for the sole purpose
      of having them make modifications exclusively for you, or provide you
      with facilities for running those works, provided that you comply with
      the terms of this License in conveying all material for which you do
      not control copyright.  Those thus making or running the covered works
      for you must do so exclusively on your behalf, under your direction
      and control, on terms that prohibit them from making any copies of
      your copyrighted material outside their relationship with you.

        Conveying under any other circumstances is permitted solely under
      the conditions stated below.  Sublicensing is not allowed; section 10
      makes it unnecessary.

        3. Protecting Users' Legal Rights From Anti-Circumvention Law.

        No covered work shall be deemed part of an effective technological
      measure under any applicable law fulfilling obligations under article
      11 of the WIPO copyright treaty adopted on 20 December 1996, or
      similar laws prohibiting or restricting circumvention of such
      measures.

        When you convey a covered work, you waive any legal power to forbid
      circumvention of technological measures to the extent such circumvention
      is effected by exercising rights under this License with respect to
      the covered work, and you disclaim any intention to limit operation or
      modification of the work as a means of enforcing, against the work's
      users, your or third parties' legal rights to forbid circumvention of
      technological measures.

        4. Conveying Verbatim Copies.

        You may convey verbatim copies of the Program's source code as you
      receive it, in any medium, provided that you conspicuously and
      appropriately publish on each copy an appropriate copyright notice;
      keep intact all notices stating that this License and any
      non-permissive terms added in accord with section 7 apply to the code;
      keep intact all notices of the absence of any warranty; and give all
      recipients a copy of this License along with the Program.

        You may charge any price or no price for each copy that you convey,
      and you may offer support or warranty protection for a fee.

        5. Conveying Modified Source Versions.

        You may convey a work based on the Program, or the modifications to
      produce it from the Program, in the form of source code under the
      terms of section 4, provided that you also meet all of these conditions:

          a) The work must carry prominent notices stating that you modified
          it, and giving a relevant date.

          b) The work must carry prominent notices stating that it is
          released under this License and any conditions added under section
          7.  This requirement modifies the requirement in section 4 to
          "keep intact all notices".

          c) You must license the entire work, as a whole, under this
          License to anyone who comes into possession of a copy.  This
          License will therefore apply, along with any applicable section 7
          additional terms, to the whole of the work, and all its parts,
          regardless of how they are packaged.  This License gives no
          permission to license the work in any other way, but it does not
          invalidate such permission if you have separately received it.

          d) If the work has interactive user interfaces, each must display
          Appropriate Legal Notices; however, if the Program has interactive
          interfaces that do not display Appropriate Legal Notices, your
          work need not make them do so.

        A compilation of a covered work with other separate and independent
      works, which are not by their nature extensions of the covered work,
      and which are not combined with it such as to form a larger program,
      in or on a volume of a storage or distribution medium, is called an
      "aggregate" if the compilation and its resulting copyright are not
      used to limit the access or legal rights of the compilation's users
      beyond what the individual works permit.  Inclusion of a covered work
      in an aggregate does not cause this License to apply to the other
      parts of the aggregate.

        6. Conveying Non-Source Forms.

        You may convey a covered work in object code form under the terms
      of sections 4 and 5, provided that you also convey the
      machine-readable Corresponding Source under the terms of this License,
      in one of these ways:

          a) Convey the object code in, or embodied in, a physical product
          (including a physical distribution medium), accompanied by the
          Corresponding Source fixed on a durable physical medium
          customarily used for software interchange.

          b) Convey the object code in, or embodied in, a physical product
          (including a physical distribution medium), accompanied by a
          written offer, valid for at least three years and valid for as
          long as you offer spare parts or customer support for that product
          model, to give anyone who possesses the object code either (1) a
          copy of the Corresponding Source for all the software in the
          product that is covered by this License, on a durable physical
          medium customarily used for software interchange, for a price no
          more than your reasonable cost of physically performing this
          conveying of source, or (2) access to copy the
          Corresponding Source from a network server at no charge.

          c) Convey individual copies of the object code with a copy of the
          written offer to provide the Corresponding Source.  This
          alternative is allowed only occasionally and noncommercially, and
          only if you received the object code with such an offer, in accord
          with subsection 6b.

          d) Convey the object code by offering access from a designated
          place (gratis or for a charge), and offer equivalent access to the
          Corresponding Source in the same way through the same place at no
          further charge.  You need not require recipients to copy the
          Corresponding Source along with the object code.  If the place to
          copy the object code is a network server, the Corresponding Source
          may be on a different server (operated by you or a third party)
          that supports equivalent copying facilities, provided you maintain
          clear directions next to the object code saying where to find the
          Corresponding Source.  Regardless of what server hosts the
          Corresponding Source, you remain obligated to ensure that it is
          available for as long as needed to satisfy these requirements.

          e) Convey the object code using peer-to-peer transmission, provided
          you inform other peers where the object code and Corresponding
          Source of the work are being offered to the general public at no
          charge under subsection 6d.

        A separable portion of the object code, whose source code is excluded
      from the Corresponding Source as a System Library, need not be
      included in conveying the object code work.

        A "User Product" is either (1) a "consumer product", which means any
      tangible personal property which is normally used for personal, family,
      or household purposes, or (2) anything designed or sold for incorporation
      into a dwelling.  In determining whether a product is a consumer product,
      doubtful cases shall be resolved in favor of coverage.  For a particular
      product received by a particular user, "normally used" refers to a
      typical or common use of that class of product, regardless of the status
      of the particular user or of the way in which the particular user
      actually uses, or expects or is expected to use, the product.  A product
      is a consumer product regardless of whether the product has substantial
      commercial, industrial or non-consumer uses, unless such uses represent
      the only significant mode of use of the product.

        "Installation Information" for a User Product means any methods,
      procedures, authorization keys, or other information required to install
      and execute modified versions of a covered work in that User Product from
      a modified version of its Corresponding Source.  The information must
      suffice to ensure that the continued functioning of the modified object
      code is in no case prevented or interfered with solely because
      modification has been made.

        If you convey an object code work under this section in, or with, or
      specifically for use in, a User Product, and the conveying occurs as
      part of a transaction in which the right of possession and use of the
      User Product is transferred to the recipient in perpetuity or for a
      fixed term (regardless of how the transaction is characterized), the
      Corresponding Source conveyed under this section must be accompanied
      by the Installation Information.  But this requirement does not apply
      if neither you nor any third party retains the ability to install
      modified object code on the User Product (for example, the work has
      been installed in ROM).

        The requirement to provide Installation Information does not include a
      requirement to continue to provide support service, warranty, or updates
      for a work that has been modified or installed by the recipient, or for
      the User Product in which it has been modified or installed.  Access to a
      network may be denied when the modification itself materially and
      adversely affects the operation of the network or violates the rules and
      protocols for communication across the network.

        Corresponding Source conveyed, and Installation Information provided,
      in accord with this section must be in a format that is publicly
      documented (and with an implementation available to the public in
      source code form), and must require no special password or key for
      unpacking, reading or copying.

        7. Additional Terms.

        "Additional permissions" are terms that supplement the terms of this
      License by making exceptions from one or more of its conditions.
      Additional permissions that are applicable to the entire Program shall
      be treated as though they were included in this License, to the extent
      that they are valid under applicable law.  If additional permissions
      apply only to part of the Program, that part may be used separately
      under those permissions, but the entire Program remains governed by
      this License without regard to the additional permissions.

        When you convey a copy of a covered work, you may at your option
      remove any additional permissions from that copy, or from any part of
      it.  (Additional permissions may be written to require their own
      removal in certain cases when you modify the work.)  You may place
      additional permissions on material, added by you to a covered work,
      for which you have or can give appropriate copyright permission.

        Notwithstanding any other provision of this License, for material you
      add to a covered work, you may (if authorized by the copyright holders of
      that material) supplement the terms of this License with terms:

          a) Disclaiming warranty or limiting liability differently from the
          terms of sections 15 and 16 of this License; or

          b) Requiring preservation of specified reasonable legal notices or
          author attributions in that material or in the Appropriate Legal
          Notices displayed by works containing it; or

          c) Prohibiting misrepresentation of the origin of that material, or
          requiring that modified versions of such material be marked in
          reasonable ways as different from the original version; or

          d) Limiting the use for publicity purposes of names of licensors or
          authors of the material; or

          e) Declining to grant rights under trademark law for use of some
          trade names, trademarks, or service marks; or

          f) Requiring indemnification of licensors and authors of that
          material by anyone who conveys the material (or modified versions of
          it) with contractual assumptions of liability to the recipient, for
          any liability that these contractual assumptions directly impose on
          those licensors and authors.

        All other non-permissive additional terms are considered "further
      restrictions" within the meaning of section 10.  If the Program as you
      received it, or any part of it, contains a notice stating that it is
      governed by this License along with a term that is a further
      restriction, you may remove that term.  If a license document contains
      a further restriction but permits relicensing or conveying under this
      License, you may add to a covered work material governed by the terms
      of that license document, provided that the further restriction does
      not survive such relicensing or conveying.

        If you add terms to a covered work in accord with this section, you
      must place, in the relevant source files, a statement of the
      additional terms that apply to those files, or a notice indicating
      where to find the applicable terms.

        Additional terms, permissive or non-permissive, may be stated in the
      form of a separately written license, or stated as exceptions;
      the above requirements apply either way.

        8. Termination.

        You may not propagate or modify a covered work except as expressly
      provided under this License.  Any attempt otherwise to propagate or
      modify it is void, and will automatically terminate your rights under
      this License (including any patent licenses granted under the third
      paragraph of section 11).

        However, if you cease all violation of this License, then your
      license from a particular copyright holder is reinstated (a)
      provisionally, unless and until the copyright holder explicitly and
      finally terminates your license, and (b) permanently, if the copyright
      holder fails to notify you of the violation by some reasonable means
      prior to 60 days after the cessation.

        Moreover, your license from a particular copyright holder is
      reinstated permanently if the copyright holder notifies you of the
      violation by some reasonable means, this is the first time you have
      received notice of violation of this License (for any work) from that
      copyright holder, and you cure the violation prior to 30 days after
      your receipt of the notice.

        Termination of your rights under this section does not terminate the
      licenses of parties who have received copies or rights from you under
      this License.  If your rights have been terminated and not permanently
      reinstated, you do not qualify to receive new licenses for the same
      material under section 10.

        9. Acceptance Not Required for Having Copies.

        You are not required to accept this License in order to receive or
      run a copy of the Program.  Ancillary propagation of a covered work
      occurring solely as a consequence of using peer-to-peer transmission
      to receive a copy likewise does not require acceptance.  However,
      nothing other than this License grants you permission to propagate or
      modify any covered work.  These actions infringe copyright if you do
      not accept this License.  Therefore, by modifying or propagating a
      covered work, you indicate your acceptance of this License to do so.

        10. Automatic Licensing of Downstream Recipients.

        Each time you convey a covered work, the recipient automatically
      receives a license from the original licensors, to run, modify and
      propagate that work, subject to this License.  You are not responsible
      for enforcing compliance by third parties with this License.

        An "entity transaction" is a transaction transferring control of an
      organization, or substantially all assets of one, or subdividing an
      organization, or merging organizations.  If propagation of a covered
      work results from an entity transaction, each party to that
      transaction who receives a copy of the work also receives whatever
      licenses to the work the party's predecessor in interest had or could
      give under the previous paragraph, plus a right to possession of the
      Corresponding Source of the work from the predecessor in interest, if
      the predecessor has it or can get it with reasonable efforts.

        You may not impose any further restrictions on the exercise of the
      rights granted or affirmed under this License.  For example, you may
      not impose a license fee, royalty, or other charge for exercise of
      rights granted under this License, and you may not initiate litigation
      (including a cross-claim or counterclaim in a lawsuit) alleging that
      any patent claim is infringed by making, using, selling, offering for
      sale, or importing the Program or any portion of it.

        11. Patents.

        A "contributor" is a copyright holder who authorizes use under this
      License of the Program or a work on which the Program is based.  The
      work thus licensed is called the contributor's "contributor version".

        A contributor's "essential patent claims" are all patent claims
      owned or controlled by the contributor, whether already acquired or
      hereafter acquired, that would be infringed by some manner, permitted
      by this License, of making, using, or selling its contributor version,
      but do not include claims that would be infringed only as a
      consequence of further modification of the contributor version.  For
      purposes of this definition, "control" includes the right to grant
      patent sublicenses in a manner consistent with the requirements of
      this License.

        Each contributor grants you a non-exclusive, worldwide, royalty-free
      patent license under the contributor's essential patent claims, to
      make, use, sell, offer for sale, import and otherwise run, modify and
      propagate the contents of its contributor version.

        In the following three paragraphs, a "patent license" is any express
      agreement or commitment, however denominated, not to enforce a patent
      (such as an express permission to practice a patent or covenant not to
      sue for patent infringement).  To "grant" such a patent license to a
      party means to make such an agreement or commitment not to enforce a
      patent against the party.

        If you convey a covered work, knowingly relying on a patent license,
      and the Corresponding Source of the work is not available for anyone
      to copy, free of charge and under the terms of this License, through a
      publicly available network server or other readily accessible means,
      then you must either (1) cause the Corresponding Source to be so
      available, or (2) arrange to deprive yourself of the benefit of the
      patent license for this particular work, or (3) arrange, in a manner
      consistent with the requirements of this License, to extend the patent
      license to downstream recipients.  "Knowingly relying" means you have
      actual knowledge that, but for the patent license, your conveying the
      covered work in a country, or your recipient's use of the covered work
      in a country, would infringe one or more identifiable patents in that
      country that you have reason to believe are valid.

        If, pursuant to or in connection with a single transaction or
      arrangement, you convey, or propagate by procuring conveyance of, a
      covered work, and grant a patent license to some of the parties
      receiving the covered work authorizing them to use, propagate, modify
      or convey a specific copy of the covered work, then the patent license
      you grant is automatically extended to all recipients of the covered
      work and works based on it.

        A patent license is "discriminatory" if it does not include within
      the scope of its coverage, prohibits the exercise of, or is
      conditioned on the non-exercise of one or more of the rights that are
      specifically granted under this License.  You may not convey a covered
      work if you are a party to an arrangement with a third party that is
      in the business of distributing software, under which you make payment
      to the third party based on the extent of your activity of conveying
      the work, and under which the third party grants, to any of the
      parties who would receive the covered work from you, a discriminatory
      patent license (a) in connection with copies of the covered work
      conveyed by you (or copies made from those copies), or (b) primarily
      for and in connection with specific products or compilations that
      contain the covered work, unless you entered into that arrangement,
      or that patent license was granted, prior to 28 March 2007.

        Nothing in this License shall be construed as excluding or limiting
      any implied license or other defenses to infringement that may
      otherwise be available to you under applicable patent law.

        12. No Surrender of Others' Freedom.

        If conditions are imposed on you (whether by court order, agreement or
      otherwise) that contradict the conditions of this License, they do not
      excuse you from the conditions of this License.  If you cannot convey a
      covered work so as to satisfy simultaneously your obligations under this
      License and any other pertinent obligations, then as a consequence you may
      not convey it at all.  For example, if you agree to terms that obligate you
      to collect a royalty for further conveying from those to whom you convey
      the Program, the only way you could satisfy both those terms and this
      License would be to refrain entirely from conveying the Program.

        13. Use with the GNU Affero General Public License.

        Notwithstanding any other provision of this License, you have
      permission to link or combine any covered work with a work licensed
      under version 3 of the GNU Affero General Public License into a single
      combined work, and to convey the resulting work.  The terms of this
      License will continue to apply to the part which is the covered work,
      but the special requirements of the GNU Affero General Public License,
      section 13, concerning interaction through a network will apply to the
      combination as such.

        14. Revised Versions of this License.

        The Free Software Foundation may publish revised and/or new versions of
      the GNU General Public License from time to time.  Such new versions will
      be similar in spirit to the present version, but may differ in detail to
      address new problems or concerns.

        Each version is given a distinguishing version number.  If the
      Program specifies that a certain numbered version of the GNU General
      Public License "or any later version" applies to it, you have the
      option of following the terms and conditions either of that numbered
      version or of any later version published by the Free Software
      Foundation.  If the Program does not specify a version number of the
      GNU General Public License, you may choose any version ever published
      by the Free Software Foundation.

        If the Program specifies that a proxy can decide which future
      versions of the GNU General Public License can be used, that proxy's
      public statement of acceptance of a version permanently authorizes you
      to choose that version for the Program.

        Later license versions may give you additional or different
      permissions.  However, no additional obligations are imposed on any
      author or copyright holder as a result of your choosing to follow a
      later version.

        15. Disclaimer of Warranty.

        THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY
      APPLICABLE LAW.  EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT
      HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM "AS IS" WITHOUT WARRANTY
      OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,
      THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
      PURPOSE.  THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM
      IS WITH YOU.  SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF
      ALL NECESSARY SERVICING, REPAIR OR CORRECTION.

        16. Limitation of Liability.

        IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
      WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS
      THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY
      GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE
      USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED TO LOSS OF
      DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD
      PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS),
      EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF
      SUCH DAMAGES.

        17. Interpretation of Sections 15 and 16.

        If the disclaimer of warranty and limitation of liability provided
      above cannot be given local legal effect according to their terms,
      reviewing courts shall apply local law that most closely approximates
      an absolute waiver of all civil liability in connection with the
      Program, unless a warranty or assumption of liability accompanies a
      copy of the Program in return for a fee.

                           END OF TERMS AND CONDITIONS

                  How to Apply These Terms to Your New Programs

        If you develop a new program, and you want it to be of the greatest
      possible use to the public, the best way to achieve this is to make it
      free software which everyone can redistribute and change under these terms.

        To do so, attach the following notices to the program.  It is safest
      to attach them to the start of each source file to most effectively
      state the exclusion of warranty; and each file should have at least
      the "copyright" line and a pointer to where the full notice is found.

          <one line to give the program's name and a brief idea of what it does.>
          Copyright (C) <year>  <name of author>

          This program is free software: you can redistribute it and/or modify
          it under the terms of the GNU General Public License as published by
          the Free Software Foundation, either version 3 of the License, or
          (at your option) any later version.

          This program is distributed in the hope that it will be useful,
          but WITHOUT ANY WARRANTY; without even the implied warranty of
          MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
          GNU General Public License for more details.

          You should have received a copy of the GNU General Public License
          along with this program.  If not, see <http://www.gnu.org/licenses/>.

      Also add information on how to contact you by electronic and paper mail.

        If the program does terminal interaction, make it output a short
      notice like this when it starts in an interactive mode:

          <program>  Copyright (C) <year>  <name of author>
          This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
          This is free software, and you are welcome to redistribute it
          under certain conditions; type `show c' for details.

      The hypothetical commands `show w' and `show c' should show the appropriate
      parts of the General Public License.  Of course, your program's commands
      might be different; for a GUI interface, you would use an "about box".

        You should also get your employer (if you work as a programmer) or school,
      if any, to sign a "copyright disclaimer" for the program, if necessary.
      For more information on this, and how to apply and follow the GNU GPL, see
      <http://www.gnu.org/licenses/>.

        The GNU General Public License does not permit incorporating your program
      into proprietary programs.  If your program is a subroutine library, you
      may consider it more useful to permit linking proprietary applications with
      the library.  If this is what you want to do, use the GNU Lesser General
      Public License instead of this License.  But first, please read
      <http://www.gnu.org/philosophy/why-not-lgpl.html>.""";

    private static final String LGPL_2_1_LICENSE = """
                          GNU LESSER GENERAL PUBLIC LICENSE
                             Version 2.1,  February 1999

       Copyright (C) 1991, 1999 Free Software Foundation, Inc.
       51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
       Everyone is permitted to copy and distribute verbatim copies
       of this license document, but changing it is not allowed.

      [This is the first released version of the Lesser GPL.  It also counts
       as the successor of the GNU Library Public License, version 2, hence
       the version number 2.1.]

                                  Preamble

        The licenses for most software are designed to take away your
      freedom to share and change it.  By contrast, the GNU General Public
      Licenses are intended to guarantee your freedom to share and change
      free software--to make sure the software is free for all its users.

        This license, the Lesser General Public License, applies to some
      specially designated software packages--typically libraries--of the
      Free Software Foundation and other authors who decide to use it.  You
      can use it too, but we suggest you first think carefully about whether
      this license or the ordinary General Public License is the better
      strategy to use in any particular case, based on the explanations below.

        When we speak of free software, we are referring to freedom of use,
      not price.  Our General Public Licenses are designed to make sure that
      you have the freedom to distribute copies of free software (and charge
      for this service if you wish); that you receive source code or can get
      it if you want it; that you can change the software and use pieces of
      it in new free programs; and that you are informed that you can do
      these things.

        To protect your rights, we need to make restrictions that forbid
      distributors to deny you these rights or to ask you to surrender these
      rights.  These restrictions translate to certain responsibilities for
      you if you distribute copies of the library or if you modify it.

        For example, if you distribute copies of the library, whether gratis
      or for a fee, you must give the recipients all the rights that we gave
      you.  You must make sure that they, too, receive or can get the source
      code.  If you link other code with the library, you must provide
      complete object files to the recipients, so that they can relink them
      with the library after making changes to the library and recompiling
      it.  And you must show them these terms so they know their rights.

        We protect your rights with a two-step method: (1) we copyright the
      library, and (2) we offer you this license, which gives you legal
      permission to copy, distribute and/or modify the library.

        To protect each distributor, we want to make it very clear that
      there is no warranty for the free library.  Also, if the library is
      modified by someone else and passed on, the recipients should know
      that what they have is not the original version, so that the original
      author's reputation will not be affected by problems that might be
      introduced by others.
      \f
        Finally, software patents pose a constant threat to the existence of
      any free program.  We wish to make sure that a company cannot
      effectively restrict the users of a free program by obtaining a
      restrictive license from a patent holder.  Therefore, we insist that
      any patent license obtained for a version of the library must be
      consistent with the full freedom of use specified in this license.

        Most GNU software, including some libraries, is covered by the
      ordinary GNU General Public License.  This license, the GNU Lesser
      General Public License, applies to certain designated libraries, and
      is quite different from the ordinary General Public License.  We use
      this license for certain libraries in order to permit linking those
      libraries into non-free programs.

        When a program is linked with a library, whether statically or using
      a shared library, the combination of the two is legally speaking a
      combined work, a derivative of the original library.  The ordinary
      General Public License therefore permits such linking only if the
      entire combination fits its criteria of freedom.  The Lesser General
      Public License permits more lax criteria for linking other code with
      the library.

        We call this license the "Lesser" General Public License because it
      does Less to protect the user's freedom than the ordinary General
      Public License.  It also provides other free software developers Less
      of an advantage over competing non-free programs.  These disadvantages
      are the reason we use the ordinary General Public License for many
      libraries.  However, the Lesser license provides advantages in certain
      special circumstances.

        For example, on rare occasions, there may be a special need to
      encourage the widest possible use of a certain library, so that it becomes
      a de-facto standard.  To achieve this, non-free programs must be
      allowed to use the library.  A more frequent case is that a free
      library does the same job as widely used non-free libraries.  In this
      case, there is little to gain by limiting the free library to free
      software only, so we use the Lesser General Public License.

        In other cases, permission to use a particular library in non-free
      programs enables a greater number of people to use a large body of
      free software.  For example, permission to use the GNU C Library in
      non-free programs enables many more people to use the whole GNU
      operating system, as well as its variant, the GNU/Linux operating
      system.

        Although the Lesser General Public License is Less protective of the
      users' freedom, it does ensure that the user of a program that is
      linked with the Library has the freedom and the wherewithal to run
      that program using a modified version of the Library.

        The precise terms and conditions for copying, distribution and
      modification follow.  Pay close attention to the difference between a
      "work based on the library" and a "work that uses the library".  The
      former contains code derived from the library, whereas the latter must
      be combined with the library in order to run.
      \f
                        GNU LESSER GENERAL PUBLIC LICENSE
         TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION

        0. This License Agreement applies to any software library or other
      program which contains a notice placed by the copyright holder or
      other authorized party saying it may be distributed under the terms of
      this Lesser General Public License (also called "this License").
      Each licensee is addressed as "you".

        A "library" means a collection of software functions and/or data
      prepared so as to be conveniently linked with application programs
      (which use some of those functions and data) to form executables.

        The "Library", below, refers to any such software library or work
      which has been distributed under these terms.  A "work based on the
      Library" means either the Library or any derivative work under
      copyright law: that is to say, a work containing the Library or a
      portion of it, either verbatim or with modifications and/or translated
      straightforwardly into another language.  (Hereinafter, translation is
      included without limitation in the term "modification".)

        "Source code" for a work means the preferred form of the work for
      making modifications to it.  For a library, complete source code means
      all the source code for all modules it contains, plus any associated
      interface definition files, plus the scripts used to control compilation
      and installation of the library.

        Activities other than copying, distribution and modification are not
      covered by this License; they are outside its scope.  The act of
      running a program using the Library is not restricted, and output from
      such a program is covered only if its contents constitute a work based
      on the Library (independent of the use of the Library in a tool for
      writing it).  Whether that is true depends on what the Library does
      and what the program that uses the Library does.

        1. You may copy and distribute verbatim copies of the Library's
      complete source code as you receive it, in any medium, provided that
      you conspicuously and appropriately publish on each copy an
      appropriate copyright notice and disclaimer of warranty; keep intact
      all the notices that refer to this License and to the absence of any
      warranty; and distribute a copy of this License along with the
      Library.

        You may charge a fee for the physical act of transferring a copy,
      and you may at your option offer warranty protection in exchange for a
      fee.
      \f
        2. You may modify your copy or copies of the Library or any portion
      of it, thus forming a work based on the Library, and copy and
      distribute such modifications or work under the terms of Section 1
      above, provided that you also meet all of these conditions:

          a) The modified work must itself be a software library.

          b) You must cause the files modified to carry prominent notices
          stating that you changed the files and the date of any change.

          c) You must cause the whole of the work to be licensed at no
          charge to all third parties under the terms of this License.

          d) If a facility in the modified Library refers to a function or a
          table of data to be supplied by an application program that uses
          the facility, other than as an argument passed when the facility
          is invoked, then you must make a good faith effort to ensure that,
          in the event an application does not supply such function or
          table, the facility still operates, and performs whatever part of
          its purpose remains meaningful.

          (For example, a function in a library to compute square roots has
          a purpose that is entirely well-defined independent of the
          application.  Therefore, Subsection 2d requires that any
          application-supplied function or table used by this function must
          be optional: if the application does not supply it, the square
          root function must still compute square roots.)

      These requirements apply to the modified work as a whole.  If
      identifiable sections of that work are not derived from the Library,
      and can be reasonably considered independent and separate works in
      themselves, then this License, and its terms, do not apply to those
      sections when you distribute them as separate works.  But when you
      distribute the same sections as part of a whole which is a work based
      on the Library, the distribution of the whole must be on the terms of
      this License, whose permissions for other licensees extend to the
      entire whole, and thus to each and every part regardless of who wrote
      it.

      Thus, it is not the intent of this section to claim rights or contest
      your rights to work written entirely by you; rather, the intent is to
      exercise the right to control the distribution of derivative or
      collective works based on the Library.

      In addition, mere aggregation of another work not based on the Library
      with the Library (or with a work based on the Library) on a volume of
      a storage or distribution medium does not bring the other work under
      the scope of this License.

        3. You may opt to apply the terms of the ordinary GNU General Public
      License instead of this License to a given copy of the Library.  To do
      this, you must alter all the notices that refer to this License, so
      that they refer to the ordinary GNU General Public License, version 2,
      instead of to this License.  (If a newer version than version 2 of the
      ordinary GNU General Public License has appeared, then you can specify
      that version instead if you wish.)  Do not make any other change in
      these notices.
      \f
        Once this change is made in a given copy, it is irreversible for
      that copy, so the ordinary GNU General Public License applies to all
      subsequent copies and derivative works made from that copy.

        This option is useful when you wish to copy part of the code of
      the Library into a program that is not a library.

        4. You may copy and distribute the Library (or a portion or
      derivative of it, under Section 2) in object code or executable form
      under the terms of Sections 1 and 2 above provided that you accompany
      it with the complete corresponding machine-readable source code, which
      must be distributed under the terms of Sections 1 and 2 above on a
      medium customarily used for software interchange.

        If distribution of object code is made by offering access to copy
      from a designated place, then offering equivalent access to copy the
      source code from the same place satisfies the requirement to
      distribute the source code, even though third parties are not
      compelled to copy the source along with the object code.

        5. A program that contains no derivative of any portion of the
      Library, but is designed to work with the Library by being compiled or
      linked with it, is called a "work that uses the Library".  Such a
      work, in isolation, is not a derivative work of the Library, and
      therefore falls outside the scope of this License.

        However, linking a "work that uses the Library" with the Library
      creates an executable that is a derivative of the Library (because it
      contains portions of the Library), rather than a "work that uses the
      library".  The executable is therefore covered by this License.
      Section 6 states terms for distribution of such executables.

        When a "work that uses the Library" uses material from a header file
      that is part of the Library, the object code for the work may be a
      derivative work of the Library even though the source code is not.
      Whether this is true is especially significant if the work can be
      linked without the Library, or if the work is itself a library.  The
      threshold for this to be true is not precisely defined by law.

        If such an object file uses only numerical parameters, data
      structure layouts and accessors, and small macros and small inline
      functions (ten lines or less in length), then the use of the object
      file is unrestricted, regardless of whether it is legally a derivative
      work.  (Executables containing this object code plus portions of the
      Library will still fall under Section 6.)

        Otherwise, if the work is a derivative of the Library, you may
      distribute the object code for the work under the terms of Section 6.
      Any executables containing that work also fall under Section 6,
      whether or not they are linked directly with the Library itself.
      \f
        6. As an exception to the Sections above, you may also combine or
      link a "work that uses the Library" with the Library to produce a
      work containing portions of the Library, and distribute that work
      under terms of your choice, provided that the terms permit
      modification of the work for the customer's own use and reverse
      engineering for debugging such modifications.

        You must give prominent notice with each copy of the work that the
      Library is used in it and that the Library and its use are covered by
      this License.  You must supply a copy of this License.  If the work
      during execution displays copyright notices, you must include the
      copyright notice for the Library among them, as well as a reference
      directing the user to the copy of this License.  Also, you must do one
      of these things:

          a) Accompany the work with the complete corresponding
          machine-readable source code for the Library including whatever
          changes were used in the work (which must be distributed under
          Sections 1 and 2 above); and, if the work is an executable linked
          with the Library, with the complete machine-readable "work that
          uses the Library", as object code and/or source code, so that the
          user can modify the Library and then relink to produce a modified
          executable containing the modified Library.  (It is understood
          that the user who changes the contents of definitions files in the
          Library will not necessarily be able to recompile the application
          to use the modified definitions.)

          b) Use a suitable shared library mechanism for linking with the
          Library.  A suitable mechanism is one that (1) uses at run time a
          copy of the library already present on the user's computer system,
          rather than copying library functions into the executable, and (2)
          will operate properly with a modified version of the library, if
          the user installs one, as long as the modified version is
          interface-compatible with the version that the work was made with.

          c) Accompany the work with a written offer, valid for at
          least three years, to give the same user the materials
          specified in Subsection 6a, above, for a charge no more
          than the cost of performing this distribution.

          d) If distribution of the work is made by offering access to copy
          from a designated place, offer equivalent access to copy the above
          specified materials from the same place.

          e) Verify that the user has already received a copy of these
          materials or that you have already sent this user a copy.

        For an executable, the required form of the "work that uses the
      Library" must include any data and utility programs needed for
      reproducing the executable from it.  However, as a special exception,
      the materials to be distributed need not include anything that is
      normally distributed (in either source or binary form) with the major
      components (compiler, kernel, and so on) of the operating system on
      which the executable runs, unless that component itself accompanies
      the executable.

        It may happen that this requirement contradicts the license
      restrictions of other proprietary libraries that do not normally
      accompany the operating system.  Such a contradiction means you cannot
      use both them and the Library together in an executable that you
      distribute.
      \f
        7. You may place library facilities that are a work based on the
      Library side-by-side in a single library together with other library
      facilities not covered by this License, and distribute such a combined
      library, provided that the separate distribution of the work based on
      the Library and of the other library facilities is otherwise
      permitted, and provided that you do these two things:

          a) Accompany the combined library with a copy of the same work
          based on the Library, uncombined with any other library
          facilities.  This must be distributed under the terms of the
          Sections above.

          b) Give prominent notice with the combined library of the fact
          that part of it is a work based on the Library, and explaining
          where to find the accompanying uncombined form of the same work.

        8. You may not copy, modify, sublicense, link with, or distribute
      the Library except as expressly provided under this License.  Any
      attempt otherwise to copy, modify, sublicense, link with, or
      distribute the Library is void, and will automatically terminate your
      rights under this License.  However, parties who have received copies,
      or rights, from you under this License will not have their licenses
      terminated so long as such parties remain in full compliance.

        9. You are not required to accept this License, since you have not
      signed it.  However, nothing else grants you permission to modify or
      distribute the Library or its derivative works.  These actions are
      prohibited by law if you do not accept this License.  Therefore, by
      modifying or distributing the Library (or any work based on the
      Library), you indicate your acceptance of this License to do so, and
      all its terms and conditions for copying, distributing or modifying
      the Library or works based on it.

        10. Each time you redistribute the Library (or any work based on the
      Library), the recipient automatically receives a license from the
      original licensor to copy, distribute, link with or modify the Library
      subject to these terms and conditions.  You may not impose any further
      restrictions on the recipients' exercise of the rights granted herein.
      You are not responsible for enforcing compliance by third parties with
      this License.
      \f
        11. If, as a consequence of a court judgment or allegation of patent
      infringement or for any other reason (not limited to patent issues),
      conditions are imposed on you (whether by court order, agreement or
      otherwise) that contradict the conditions of this License, they do not
      excuse you from the conditions of this License.  If you cannot
      distribute so as to satisfy simultaneously your obligations under this
      License and any other pertinent obligations, then as a consequence you
      may not distribute the Library at all.  For example, if a patent
      license would not permit royalty-free redistribution of the Library by
      all those who receive copies directly or indirectly through you, then
      the only way you could satisfy both it and this License would be to
      refrain entirely from distribution of the Library.

      If any portion of this section is held invalid or unenforceable under any
      particular circumstance, the balance of the section is intended to apply,
      and the section as a whole is intended to apply in other circumstances.

      It is not the purpose of this section to induce you to infringe any
      patents or other property right claims or to contest validity of any
      such claims; this section has the sole purpose of protecting the
      integrity of the free software distribution system which is
      implemented by public license practices.  Many people have made
      generous contributions to the wide range of software distributed
      through that system in reliance on consistent application of that
      system; it is up to the author/donor to decide if he or she is willing
      to distribute software through any other system and a licensee cannot
      impose that choice.

      This section is intended to make thoroughly clear what is believed to
      be a consequence of the rest of this License.

        12. If the distribution and/or use of the Library is restricted in
      certain countries either by patents or by copyrighted interfaces, the
      original copyright holder who places the Library under this License may add
      an explicit geographical distribution limitation excluding those countries,
      so that distribution is permitted only in or among countries not thus
      excluded.  In such case, this License incorporates the limitation as if
      written in the body of this License.

        13. The Free Software Foundation may publish revised and/or new
      versions of the Lesser General Public License from time to time.
      Such new versions will be similar in spirit to the present version,
      but may differ in detail to address new problems or concerns.

      Each version is given a distinguishing version number.  If the Library
      specifies a version number of this License which applies to it and
      "any later version", you have the option of following the terms and
      conditions either of that version or of any later version published by
      the Free Software Foundation.  If the Library does not specify a
      license version number, you may choose any version ever published by
      the Free Software Foundation.
      \f
        14. If you wish to incorporate parts of the Library into other free
      programs whose distribution conditions are incompatible with these,
      write to the author to ask for permission.  For software which is
      copyrighted by the Free Software Foundation, write to the Free
      Software Foundation; we sometimes make exceptions for this.  Our
      decision will be guided by the two goals of preserving the free status
      of all derivatives of our free software and of promoting the sharing
      and reuse of software generally.

                                  NO WARRANTY

        15. BECAUSE THE LIBRARY IS LICENSED FREE OF CHARGE, THERE IS NO
      WARRANTY FOR THE LIBRARY, TO THE EXTENT PERMITTED BY APPLICABLE LAW.
      EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR
      OTHER PARTIES PROVIDE THE LIBRARY "AS IS" WITHOUT WARRANTY OF ANY
      KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE
      IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
      PURPOSE.  THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE
      LIBRARY IS WITH YOU.  SHOULD THE LIBRARY PROVE DEFECTIVE, YOU ASSUME
      THE COST OF ALL NECESSARY SERVICING, REPAIR OR CORRECTION.

        16. IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN
      WRITING WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MAY MODIFY
      AND/OR REDISTRIBUTE THE LIBRARY AS PERMITTED ABOVE, BE LIABLE TO YOU
      FOR DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL OR
      CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE THE
      LIBRARY (INCLUDING BUT NOT LIMITED TO LOSS OF DATA OR DATA BEING
      RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A
      FAILURE OF THE LIBRARY TO OPERATE WITH ANY OTHER SOFTWARE), EVEN IF
      SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH
      DAMAGES.

                           END OF TERMS AND CONDITIONS
      \f
                 How to Apply These Terms to Your New Libraries

        If you develop a new library, and you want it to be of the greatest
      possible use to the public, we recommend making it free software that
      everyone can redistribute and change.  You can do so by permitting
      redistribution under these terms (or, alternatively, under the terms of the
      ordinary General Public License).

        To apply these terms, attach the following notices to the library.  It is
      safest to attach them to the start of each source file to most effectively
      convey the exclusion of warranty; and each file should have at least the
      "copyright" line and a pointer to where the full notice is found.

          <one line to give the library's name and a brief idea of what it does.>
          Copyright (C) <year>  <name of author>

          This library is free software; you can redistribute it and/or
          modify it under the terms of the GNU Lesser General Public
          License as published by the Free Software Foundation; either
          version 2.1 of the License, or (at your option) any later version.

          This library is distributed in the hope that it will be useful,
          but WITHOUT ANY WARRANTY; without even the implied warranty of
          MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
          Lesser General Public License for more details.

          You should have received a copy of the GNU Lesser General Public
          License along with this library; if not, write to the Free Software
          Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

      Also add information on how to contact you by electronic and paper mail.

      You should also get your employer (if you work as a programmer) or your
      school, if any, to sign a "copyright disclaimer" for the library, if
      necessary.  Here is a sample; alter the names:

        Yoyodyne, Inc., hereby disclaims all copyright interest in the
        library `Frob' (a library for tweaking knobs) written by James Random Hacker.

        <signature of Ty Coon>, 1 April 1990
        Ty Coon, President of Vice

      That's all there is to it!
      """;

    private static final String UPL_1_LICENSE = """
      The Universal Permissive License (UPL), Version 1.0

      Subject to the condition set forth below, permission is hereby granted to any person obtaining a copy of this software, associated documentation and/or data (collectively the "Software"), free of charge and under any and all copyright rights in the Software, and any and all patent rights owned or freely licensable by each licensor hereunder covering either (i) the unmodified Software as contributed to or provided by such licensor, or (ii) the Larger Works (as defined below), to deal in both

      (a) the Software, and

      (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if one is included with the Software each a "Larger Work" to which the Software is contributed by such licensors),

      without restriction, including without limitation the rights to copy, create derivative works of, display, perform, and distribute the Software and make, use, sell, offer for sale, import, export, have made, and have sold the Software and the Larger Work(s), and to sublicense the foregoing rights on either these or other terms.

      This license is subject to the following condition:

      The above copyright notice and either this complete permission notice or at a minimum a reference to the UPL must be included in all copies or substantial portions of the Software.

      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
      """;

    @Override
    protected int run() throws Exception {
      printOwlLicense();
      printLibraryLicense(ANTLR_4_LIBRARY, ANTLR_4_LICENSE);
      printLibraryLicense(GRAAL_LIBRARY, UPL_1_LICENSE);
      printLibraryLicense(GUAVA_LIBRARY, APACHE_2_LICENSE);
      printLibraryLicense(JBDD_LIBRARY, GPL_3_LICENSE);
      printLibraryLicense(JHOAFPARSER_LIBRARY, LGPL_2_1_LICENSE);
      printLibraryLicense(PICOCLI_LIBRARY, APACHE_2_LICENSE);
      return 0;
    }

    private static void printOwlLicense() {
      System.out.printf("Owl (Copyright (C) 2016-2021 Salomon Sickert-Zehnter and contributors)%n"
        + "License:%n%s%n%n%n", GPL_3_LICENSE);
    }

    private static void printLibraryLicense(String library, String license) {
      System.out.printf("Library: %s%nLicense:%n%s%n%n%n", library, license);
    }
  }

  @Command(
    name = "ltl-inspect",
    hidden = true // Command is hidden, since it is not finished yet.
  )
  static final class LtlInspectionCommand extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.FormulaReader formulaReader = null;

    @Override
    protected int run() throws IOException {

      try (var source = formulaReader.source()) {
        var formulaIterator = source.iterator();

        while (formulaIterator.hasNext()) {
          var formula = formulaIterator.next();

          System.out.printf("Formula: %s,%nSyntactic class: %s%n",
            formula, SyntacticFragments.FormulaClass.classify(formula.formula()));
        }
      }

      return 0;
    }
  }

  @Command(
    name = "aut-inspect",
    hidden = true // Command is hidden, since it is not finished yet.
  )
  static final class AutInspectionCommand extends AbstractOwlSubcommand {

    @Nullable
    @Option(
      names = { "-f", "--format"},
      required = true,
      description = {
        """
          The format string. Uses a reduced set of the spot syntax
          %A, %a   Number of acceptance sets
          %C, %c   Number of SCCs
          %D, %d   1 if the automaton is deterministic, 0 otherwise
          %G, %g   acceptance condition (in HOA syntax)
          %S, %s   Number of states
          %H, %h   The automaton in HOA format on a single line
          %M, %m   Name of the automaton
          %n       Newline
          %X, %x   Number of atomic propositions"""}
    )
    private String format;

    private static final Map<Pattern, Function<Automaton<?, ?>, String>> PATTERNS = Map.of(
      // Acceptance condition
      Pattern.compile("%G", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.acceptance().booleanExpression().toString(),

      // Acceptance set count
      Pattern.compile("%A", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.acceptance().acceptanceSets()),

      // Is deterministic
      Pattern.compile("%D", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> automaton.is(Automaton.Property.DETERMINISTIC) ? "1" : "0",

      // Single line HOA
      Pattern.compile("%H", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> HoaWriter.toString(automaton).replace('\n', ' '),

      // State count
      Pattern.compile("%S", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.states().size()),

      // Number of propositions
      Pattern.compile("%X", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(automaton.atomicPropositions().size()),

      // Number of SCCs
      Pattern.compile("%C", Pattern.CASE_INSENSITIVE | Pattern.LITERAL),
      automaton -> String.valueOf(SccDecomposition.of(automaton).sccs().size()),

      Pattern.compile("%n", Pattern.LITERAL),
      automaton -> "\n");

    @Mixin
    private Mixins.AutomatonReader automatonReader = null;

    @Override
    protected int run() {

      try (var source = automatonReader.source(EmersonLeiAcceptance.class)) {
        var automatonIterator = source.iterator();

        while (automatonIterator.hasNext()) {
          String result = Objects.requireNonNull(format);

          for (var pattern : PATTERNS.entrySet()) {
            Matcher matcher = pattern.getKey().matcher(result);

            if (matcher.find()) {
              String replacement = Matcher.quoteReplacement(pattern.getValue().apply(
                automatonIterator.next()));
              result = matcher.replaceAll(replacement);
            }
          }

          System.out.printf("%s%n", result);
        }
      }

      return 0;
    }
  }

  @Command(
    name = "delag",
    description = DelagMigrationCommand.DELAG_DESCRIPTION
  )
  static final class DelagMigrationCommand extends AbstractOwlSubcommand {

    private static final String DELAG_DESCRIPTION = "The functionality of the 'delag' subcommand "
      + "has been moved to the 'ltl2dela' subcommand. You can use 'owl ltl2dela -t=MS17' to access "
      + "the original 'delag' construction.";

    @Override
    protected int run() throws Exception {
      return 0;
    }
  }

  @Command(
    name = "dpa2pg",
    description =
      "Converts a deterministic parity automaton into a parity game by splitting the transitions. "
        + "This subcommand outputs the game in the PG-solver format and this command is only "
        + "expected to be used for prototyping, since in practice the resulting files are too "
        + "large.",
    hidden = true
  )
  static final class Automaton2GameCommand extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.AutomatonReader automatonReader = null;

    @Option(
      names = { "-o", "--output-file" },
      description = "Output file (default: write to stdout)."
    )
    private Path gameFile = null;

    @ArgGroup(multiplicity = "1")
    private InputsOutputs inputsOutputs = null;

    private static class InputsOutputs {
      @Option(
        names = {"-e", "--environment"},
        description = "List of atomic propositions controlled by the environment."
      )
      private String[] environment;

      @Option(
        names = {"-s", "--system"},
        description = "List of atomic propositions controlled by the system."
      )
      private String[] system;

      @Option(
        names = {"--environment-prefix"},
        description = "Prefix of atomic propositions controlled by the environment."
      )
      private String environmentPrefix;

      @Option(
        names = {"--system-prefix"},
        description = "Prefix of atomic propositions controlled by the system."
      )
      private String systemPrefix;
    }

    @Override
    protected int run() throws IOException {

      Predicate<String> environmentAtomicProposition;

      if (inputsOutputs.environment != null) {
        environmentAtomicProposition
          = Arrays.asList(inputsOutputs.environment)::contains;
      } else if (inputsOutputs.system != null) {
        environmentAtomicProposition
          = Predicate.not(Arrays.asList(inputsOutputs.system)::contains);
      } else if (inputsOutputs.systemPrefix != null) {
        environmentAtomicProposition
          = Predicate.not(inputsOutputs.systemPrefix::startsWith);
      } else {
        environmentAtomicProposition
          = inputsOutputs.environmentPrefix::startsWith;
      }

      try (var source = automatonReader.source(ParityAcceptance.class);
           var sink = new PrintWriter(gameFile == null
             ? new BufferedWriter(new OutputStreamWriter(System.out))
             : Files.newBufferedWriter(gameFile))) {

        var automatonIterator = source.iterator();

        while (automatonIterator.hasNext()) {

          var automaton = ParityUtil.convert(
            OmegaAcceptanceCast.cast(
              Views.complete(automatonIterator.next()), ParityAcceptance.class),
            ParityAcceptance.Parity.MAX_EVEN);

          checkArgument(automaton.initialStates().size() <= 1,
            "Multiple initial states are not supported");
          checkArgument(automaton.is(Automaton.Property.DETERMINISTIC),
            "Input automaton needs to be deterministic");

          PgSolverFormat.write(
            GameViews.split(automaton, environmentAtomicProposition), sink, false);
        }
      }

      return 0;
    }
  }
}
