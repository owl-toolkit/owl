package owl.ltl;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RandomConjunctions {

  public File find(String name,List<File> files) {
    for (int i = 0; i < files.size() ; i++) {
      if (files.get(i).getName().equals(name)) {
        return files.get(i);
      }
    }
    return null;
  }

  public List<File> listFilesForFolder ( final File folder) {
    List<File> out = new ArrayList<>();
    for (final File fileEntry : folder.listFiles()) {
      if (fileEntry.isDirectory()) {
        listFilesForFolder(fileEntry);
      } else {
        out.add(fileEntry);
      }
    }
    return  out;
  }
  public static void write(String content, String path) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(path));
    writer.write(content);
    writer.close();
  }
  @Test
  void Randomconjunctions() throws IOException {
    int n = 4;
    Random random = new Random();
    final File folder = new File("/home/max/Dokumente/Bachelorarbeit/Tests/vsSyft/SyftInputs");
    List<File> Files = listFilesForFolder(folder);
    List<File> LTLfFiles = new ArrayList<>();
    List<File> PartFiles = new ArrayList<>();
    for (int i = 0;i< Files.size();i++) {
      File f = Files.get(i);
      if (!f.getName().contains(".mona") && !f.getName().contains(".dfa") && !f.getName().contains(".part")) {
        LTLfFiles.add(f);
      }
      if (!f.getName().contains(".mona") && !f.getName().contains(".dfa") && !f.getName().contains(".ltlf")) {
        PartFiles.add(f);
      }
    }

    final String ZielDir = "/home/max/Dokumente/Bachelorarbeit/Tests/vsSyft/RandomConjunctions/Length"+ n+"/";
    for (int i = 0; i < 100; i++) { //how many randomconj
      File[] RLTLf = new File[n];File[] Rpart = new File[n];
      for (int j = 0; j < n ; j++) { // select random files
        int r = random.nextInt(434);
        RLTLf[j] = LTLfFiles.get(r);
        String name = RLTLf[j].getName().substring(0, RLTLf[j].getName().length() - 5) +".part";
        Rpart[j] = find(name,PartFiles);
      }
      List<String> Rliterals = new ArrayList<>();
      List<String> Rinputs = new ArrayList<>();
      List<String> Routputs = new ArrayList<>();
      String RFormula = "";
      String RName = "";
      int uniqueMarker = 0;
      for (int j = 0; j < n ; j++) {
        if (j != 0) {
          RName += "_and_"+RLTLf[j].getName().substring(0,RLTLf[j].getName().length()-5);
        } else {
          RName += RLTLf[j].getName().substring(0,RLTLf[j].getName().length()-5);
        }

        BufferedReader brPart = new BufferedReader(new FileReader(Rpart[j]));
        BufferedReader brLTLf = new BufferedReader(new FileReader(RLTLf[j]));
        String inputline = brPart.readLine();
        String outputline = brPart.readLine();
        inputline = inputline.substring(9);
        outputline = outputline.substring(10);
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        inputs.addAll(Arrays.asList(inputline.split(" ")));
        outputs.addAll(Arrays.asList(outputline.split(" ")));
        String formula = brLTLf.readLine();
        for (int k = 0; k < inputs.size(); k++) {
          String akt = inputs.get(k);
          inputs.remove(akt);
          inputs.add(k,akt+uniqueMarker);
          formula = formula.replace(akt,akt+uniqueMarker++);
        }
        for (int k = 0; k < outputs.size(); k++) {
          String akt = outputs.get(k);
          outputs.remove(akt);
          outputs.add(k,akt+uniqueMarker);
          formula = formula.replace(akt,akt+uniqueMarker++);
        }
        if (j != 0) {
          RFormula += "&" + "("+formula+")";
        } else {
          RFormula = "("+formula+")";
        }
        Rinputs.addAll(inputs);
        Routputs.addAll(outputs);
        Rliterals.addAll(inputs);
        Rliterals.addAll(outputs);
      }
      write(RFormula,ZielDir+"Syft/" + RName +".ltlf");
      String PartString = ".inputs:";
      for (int j = 0; j < Rinputs.size(); j++) {
        PartString += " " + Rinputs.get(j);
      }
      PartString += "\n.outputs:";
      for (int j = 0; j < Routputs.size(); j++) {
        PartString += " " + Routputs.get(j);
      }
      write(PartString,ZielDir+"Syft/" + RName +".part");
      String Strixstring = "./strix -x -vv -t -f \'";
      Strixstring += RFormula;
      Strixstring += "\' --ins=\"";

      for (int j = 0; j < Rinputs.size(); j++) {
        if (j != 0) {
          Strixstring += "," +Rinputs.get(j);
        } else {
          Strixstring += Rinputs.get(j);
        }

      }
      Strixstring +="\" --outs=\"";
      for (int j = 0; j < Routputs.size(); j++) {
        if (j != 0) {
          Strixstring += "," +Routputs.get(j);
        } else {
          Strixstring += Routputs.get(j);
        }

      }
      Strixstring += "\"";
      write(Strixstring,ZielDir + "strix/"+ RName + ".strix");
    }




  }
}