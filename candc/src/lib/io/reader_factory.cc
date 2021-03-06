// C&C NLP tools
// Copyright (c) Universities of Edinburgh, Oxford and Sydney
// Copyright (c) James R. Curran
//
// This software is covered by a non-commercial use licence.
// See LICENCE.txt for the full text of the licence.
//
// If LICENCE.txt is not included in this distribution
// please email candc@it.usyd.edu.au to obtain a copy.

#include "base.h"

#include "io/format.h"

#include "io/reader.h"
#include "io/reader_factory.h"
#include "io/reader_horiz.h"
#include "io/reader_multi_horiz.h"
#include "io/reader_deps_multi_horiz.h"

using namespace std;

namespace NLP { namespace IO {

ReaderFactory::ReaderFactory(const std::string &uri, const Format &fmt, const std::string &deps_uri)
  : Reader(uri, "ReaderFactory(" + std::string(fmt) + ")"), input(uri), deps_input(deps_uri == "" ? STDIN : deps_uri) {
  if(deps_uri == "")
    reader= new MultiHReader(input.stream, uri, fmt.fields, fmt.field_sep ? fmt.field_sep : '|');
  else
    reader = new DepsMultiHReader(input.stream, deps_input.stream, uri, deps_uri, fmt.fields, fmt.field_sep ? fmt.field_sep : '|');
  PREFACE = reader->PREFACE;
}


} }
