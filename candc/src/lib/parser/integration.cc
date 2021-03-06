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

#include "config/config.h"

#include "pool.h"

#include "model/model.h"

#include "io/format.h"
#include "config/format.h"

#include "io/reader.h"
#include "io/writer.h"

#include "tagger/tagdict.h"
#include "tagger/tagsetdict.h"
#include "tagger/tagger.h"
#include "tagger/pos.h"
#include "tagger/super.h"

#include "prob.h"
#include "tagger/taghist.h"
#include "tagger/nodepool.h"
#include "tagger/lattice.h"
#include "tagger/flattice.h"
#include "tagger/state.h"

#include "parser/parser.h"
#include "parser/decoder_factory.h"

#include "parser/kbest.h"

#include "parser/printer.h"

#include "parser/integration.h"

using namespace std;

namespace NLP { namespace CCG {

const static ulong DEF_START = 0;

Integration::Betas
def_betas(void){
  Integration::Betas def(5);

  def[0] = 0.075;
  def[1] = 0.03;
  def[2] = 0.01;
  def[3] = 0.005;
  def[4] = 0.001;

  return def;
}

Integration::DictCutoffs
def_dict_cutoffs(void){
  Integration::DictCutoffs def(5);

  def[0] = 20;
  def[1] = 20;
  def[2] = 20;
  def[3] = 20;
  def[4] = 150;

  return def;
}

Integration::Config::Config(const std::string &name, const std::string &desc)
  : Cfg(name, desc),
    start(*this, "start_level", "the first level of beta/dict_cutoff to use", DEF_START),
    betas(*this, "betas", "the super tagging beta levels", def_betas()),
    dict_cutoffs(*this, "dict_cutoffs", "the super tagging dict_cutoff levels", def_dict_cutoffs()),
    pos_as_super(*this, "pos_as_super", "use the pos tagger as supertagger", false){}
  
void
Integration::Config::check(void){
  Cfg::check();

  if(betas().size() != dict_cutoffs().size())
    throw ConfigError("there must be the same number of beta and dict_cutoff levels", "betas");

  if(start() >= betas().size())
    throw ConfigError("the start_level must be less than the number of beta and dict_cutoff levels", "start_level");
}

Integration::Integration(Integration::Config &int_cfg,
       POS::Config &pos_cfg, Super::Config &super_cfg,
       Parser::Config &parser_cfg,
       Sentence &sent, ulong load)
  : START(int_cfg.start()), BETAS(int_cfg.betas()),
    MIN_BETA(*std::min_element(BETAS.begin(), BETAS.end())),
    DICT_CUTOFFS(int_cfg.dict_cutoffs()), pos_as_super(int_cfg.pos_as_super()),
    pos(pos_cfg), super(super_cfg),
    cats(parser_cfg.cats(), parser_cfg.markedup(), parser_cfg.alt_markedup()),
    parser(parser_cfg, sent, cats, load),
    nsentences(0), nwords(0), nexceptions(0),
    nfail_nospan(0), nfail_explode(0), nfail_nospan_explode(0),
    nfail_explode_nospan(0), nsuccesses(BETAS.size(), 0),
    super_state(super.create_state()){}

Integration::~Integration(void){
  delete super_state;
}

bool
Integration::parse(Sentence &sent, Decoder &decoder, Printer &printer, 
    KBest ret[], const std::string meta, const bool USE_SUPER, const bool ALT_POS,
    const bool SUPER_AUTO_POS, const bool PARSER_AUTO_POS){
  double beta = 0.0;
  ulong dict_cutoff = 0;

  try {
    bool repair = false;

    ulong last_dict_cutoff = 0;
    int trial = START;
    int last = START;

    ++nsentences;
    nwords += sent.words.size();

    ushort nparses;
    parser.reset();
    while(1){
      nparses = 0;
      beta = BETAS[trial];
      dict_cutoff = DICT_CUTOFFS[trial];

      if(USE_SUPER && dict_cutoff != last_dict_cutoff){
        if(ALT_POS){
          if(SUPER_AUTO_POS)
            sent.copy_single('a', 'p');
          else
            sent.copy_single('g', 'p');
        }
        if(pos_as_super){
          pos.mtag(sent, NLP::Taggers::FWDBWD, dict_cutoff, MIN_BETA, super_state);
          sent.copy_multi_to_multi('P', 'S');
        }
        else
          super.mtag(sent, NLP::Taggers::FWDBWD, dict_cutoff, MIN_BETA, super_state);
        repair = false;
      }
      last_dict_cutoff = dict_cutoff;

      if(ALT_POS){
        if(PARSER_AUTO_POS && !SUPER_AUTO_POS)
          sent.copy_single('a', 'p');
        else if(!PARSER_AUTO_POS && SUPER_AUTO_POS)
          sent.copy_single('g', 'p');
      }
      if(sent.words.size() == 1){
        ++nsuccesses[trial];
        printer.parsed(0, 0, sent, beta, dict_cutoff, meta);
	//printer.meta ("newId", 10);
        return true;
      }else if(parser.parse(beta, repair)){
        parser.calc_scores();

        if((nparses = parser.best(ret, decoder)) != 0){
          ++nsuccesses[trial];
          printer.parsed(ret, nparses, sent, beta, dict_cutoff, meta);

          Statistics stats;
          parser.calc_stats(stats);
          printer.stats(stats);
	  //printer.meta ("newId", nparses);

          global_stats += stats;

          return true;
        }else{
          printer.attempted("nospan", sent, beta, dict_cutoff);
          if(trial - last < 0){
            ++nfail_explode_nospan;
            printer.failed("explode/nospan", sent, beta, dict_cutoff);
            return false;
          }
          last = trial++;
          repair = true;
          if(trial == static_cast<long>(BETAS.size())){
            ++nfail_nospan;
            printer.failed("no span", sent, beta, dict_cutoff);
            return false;
          }
        }
      }else{
        printer.attempted("exploded", sent, beta, dict_cutoff);
        if(trial - last > 0){
          ++nfail_nospan_explode;
          printer.failed("nospan/explode", sent, beta, dict_cutoff);
          return false;
        }
        last = trial--;
        repair = false;
        if(trial == -1){
          ++nfail_explode;
          printer.failed("explode", sent, beta, dict_cutoff);
          return false;
        }
      }
    }
  }catch(NLP::ParseError e){
    ++nexceptions;
    printer.error(e.msg, sent, beta, dict_cutoff); 
    return false;
  }
}

} }
