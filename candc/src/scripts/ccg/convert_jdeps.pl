#!/usr/bin/perl
# converts Julia's predarg dependencies into C&C parser dependencies
# C&C NLP tools
# Copyright (c) Universities of Edinburgh, Oxford and Sydney
# Copyright (c) James R. Curran
#
# This software is covered by a non-commercial use licence.
# See LICENCE.txt for the full text of the licence.
#
# If LICENCE.txt is not included in this distribution
# please email candc@it.usyd.edu.au to obtain a copy.

$command_line = "# this file was generated by the following command(s):\n";
$command_line .= "# $0 $test_file $gold_file $gold_cats\n\n";

print $command_line;

$predarg = shift;
open(PREDARG, $predarg) || die("can't open predarg dependencies file\n");

$zeroc = 0;

while(<PREDARG>){
  # for some reason some of julia's pred-arg cases have zero dependencies, and
  # don't appear to be in the other 00 files (AUTO and RAW), except for the 5th case
  if(/^<s> 0$/){
    <PREDARG>;

    $zeroc++;
    # the line below has been removed for Section 23
    # print "\n" if($zeroc == 5);
    # the line below has been added for Section 23
    # print "\n" if($zeroc == 2);

    next;
  }

  if(/^<s.*> \d+$/){
    @deps = ();
    $print = 1;
    next;
  }

  if(/^<\\s>[ ]*$/){
    if($print){
      foreach $dep (@deps){
	print "$dep\n";
      }
    }
    print "\n";
    next;
  }

  if(/^(\d+)\s+(\d+)\s+(\S+)\s+(\d+)\s+(\S+)\s+(\S+)/){
    $argn = $1 + 1;
    $predn = $2 + 1;
    $cat = $3;
    $slot = $4;
    $arg = $5;
    $pred = $6;

    push @deps, "$pred\_$predn $cat $slot $arg\_$argn";
    next;
  }

  print STDERR "unrecognized format: $_ CHECK\n";
}
close(PREDARG);
