#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import os
import sys
import cgi
import cgitb
import subprocess
import io

cgitb.enable()

TIMEOUT=60
DOT_PATH="dot"
AUTFILT_PATH="autfilt"
OWL_PATH="bin/owl"

def fail_with_message(message):
  print("Content-Type: text/html; charset=utf-8")
  print()
  print("<title>Error</title>")
  print(cgi.escape(message.encode('ascii', 'xmlcharrefreplace').decode('ascii')).replace("\n", "<br>"))
  sys.exit(1)

def process():
  form = cgi.FieldStorage()

  def get_bool(name, default):
    value = form.getvalue(name, default=None)
    if value is None:
      return default
    if value == "1":
      return True
    return False

  format = form.getvalue("format", default=None)
  formula = form.getvalue("formula", default=None)
  annotations = get_bool("annotations", False)
  state_acc = get_bool("state-acc", False)
  show_scc = get_bool("show-scc", False)

  if not formula or formula.strip() == "":
    fail_with_message("No formula specified")
  formula = formula.strip()

  if not format or format.strip() == "":
    fail_with_message("No format specified")

  args = [OWL_PATH]
  if annotations:
    args.append("--annotations")

  args.extend(["-i", formula, "---", "ltl", "---", "simplify-ltl", "---"])

  if format == "nba":
    args.append("ltl2nba")
  elif format == "ngba":
    args.append("ltl2ngba")
  elif format == "ldba-symmetric":
    args.append("ltl2ldba")
    args.append("--symmetric")
  elif format == "ldgba-symmetric":
    args.append("ltl2ldgba")
    args.append("--symmetric")
  elif format == "ldba-asymmetric":
    args.append("ltl2ldba")
    args.append("--asymmetric")
  elif format == "ldgba-asymmetric":
    args.append("ltl2ldgba")
    args.append("--asymmetric")
  elif format == "dra-symmetric":
    args.append("ltl2dra")
    args.append("--symmetric")
  elif format == "dgra-symmetric":
    args.append("ltl2dgra")
    args.append("--symmetric")
  elif format == "dra-asymmetric":
    args.append("ltl2dra")
    args.append("--asymmetric")
  elif format == "dgra-asymmetric":
    args.append("ltl2dgra")
    args.append("--asymmetric")
  elif format == "dpa-symmetric":
    args.append("ltl2dpa")
    args.append("--symmetric")
  elif format == "dpa-asymmetric":
    args.append("ltl2dpa")
    args.append("--asymmetric")
  elif format == "delag":
    args.append("delag")
  elif format == "na":
    args.append("ltl2na")
  elif format == "da":
    args.append("ltl2da")
  else:
    fail_with_message("Unknown format {}".format(format))

  args.extend(["---", "optimize-aut"])
  args.extend(["---", "hoa"])

  if state_acc:
    args.append("--state-acceptance")

  # Process part

  # TODO One could directly chain the processes.
  # TODO Figure out what is needed here maybe
  env = os.environ.copy()

  process = subprocess.Popen(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
  try:
    stdout, stderr = process.communicate(timeout=TIMEOUT)
  except subprocess.TimeoutExpired:
    fail_with_message("Timeout - try a smaller formula!")
  if process.returncode != 0:
    fail_with_message("An error occured while translating the formula \"{}\": {}\n{}\n{}".format(formula, process.returncode, stdout, stderr))

  hoa_string = stdout
  if hoa_string is None or hoa_string == "":
    fail_with_message("Something went wrong! " + stderr)

  autfilt_env = env.copy()
  # b: Bullets for acceptance, a: print acceptance, h: horizontal layout, R: colours for acceptance, C: state colour, f: Font, n: display name, k: use state labels
  autfilt_dot_args = 'BavC(#ffffa0)f(Roboto)k'

  if show_scc:
    autfilt_dot_args += "s"

  autfilt_env['SPOT_DOTDEFAULT'] = autfilt_dot_args
  autfilt_env['SPOT_DOTEXTRA'] = 'edge[arrowhead=vee, arrowsize=.7]'
  # automaton_name = "Automaton for {}".format(formula)
  # --name breaks annotations printing
  # autfilt_args = [AUTFILT_PATH, "--dot", "--name={}".format(automaton_name)]
  autfilt_args = [AUTFILT_PATH, "--dot", "-8"]
  # if simplify:
  #  autfilt_args.append("--high")
  # if highlight_word is not None:
  #  autfilt_args.append("--highlight-word=0,{}".format(highlight_word.strip()))

  autfilt_process = subprocess.Popen(autfilt_args, env=autfilt_env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  try:
    stdout, stderr = autfilt_process.communicate(input=hoa_string.encode("utf-8"), timeout=TIMEOUT)
  except subprocess.TimeoutExpired:
    fail_with_message("Timeout while waiting for autfilt")
  if autfilt_process.returncode != 0:
    fail_with_message("An error occured while running autfilt: {}\n{}".format(autfilt_process.returncode, stderr.decode("utf-8")))
  dot_bytes = stdout

  dot_process = subprocess.Popen([DOT_PATH, "-Tsvg"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  try:
    stdout, stderr = dot_process.communicate(input=dot_bytes, timeout=TIMEOUT)
  except subprocess.TimeoutExpired:
    fail_with_message("Timeout while waiting for dot")
  if dot_process.returncode != 0:
    fail_with_message("An error occured while running dot: {}\n{}".format(dot_process.returncode, stderr.decode("utf-8")))

  image_bytes = stdout
  if not image_bytes:
    fail_with_message("Dot failed")

  # Need to write directly to buffer - print() uses the default charset to encode the given strings, see http://stackoverflow.com/questions/14860034/python-cgi-utf-8-doesnt-work
  sys.stdout.write('Content-type: image/svg+xml; charset=utf-8\r\n\r\n')
  sys.stdout.flush()
  sys.stdout.buffer.write(image_bytes)
  sys.exit(0)


if __name__ == "__main__":
  try:
    process()
  except Exception as e:
    fail_with_error("Something went wrong! {}".format(e))
