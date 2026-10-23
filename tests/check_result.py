# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""从产品桥直接提取 result()；仅需 Python 3 和 clang++，不读取研究证据。"""
import argparse
import subprocess
import tempfile
from pathlib import Path
R=Path(__file__).resolve().parents[1]
def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--source',type=Path,default=R/'native/replay_bridge.cpp')
 source=parser.parse_args().source.read_text()
 body=source[source.index('bool result(void* game){'):source.index('using Presentation=')]
 code=r'''
#include <atomic>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <array>
#define LOG(...) ((void)0)
template<class T>T read(void* p,unsigned o){T v;std::memcpy(&v,static_cast<char*>(p)+o,sizeof(v));return v;}
template<class T>void put(void* p,unsigned o,T v){std::memcpy(static_cast<char*>(p)+o,&v,sizeof(v));}
uint32_t identity[2]={11,12};int owner=-1,actual_crowns[2];std::string completion;std::atomic<bool> finished{false};
''' + body + r'''
struct Obj{std::array<char,0x200> o{};std::array<char,0x600> data{};std::array<char,32> vt{},parts{},health{};};
int objectKind(void* o){return read<int>(o,0x84);}
struct Fixture{
 std::array<char,0x500> world{};std::array<char,0x100> game{},manager{},objects{};std::array<void*,32> vector{};std::array<Obj,32> obj{};uint32_t ids[2][2]={{11,12},{21,22}};int count=0;
 Fixture(){owner=-1;finished=false;identity[0]=11;identity[1]=12;put(game.data(),0xa8,(void*)world.data());put(game.data(),0x60,5000);put(world.data(),0x1e0,uint8_t(1));put(world.data(),0xe0,(void*)manager.data());put(manager.data(),0x10,(void*)objects.data());put(objects.data(),8,(void*)vector.data());for(int s=0;s<2;s++)put(world.data(),0x30+8*s,(void*)ids[s]);}
 void add(int side,bool king,bool tower,int hp,int x=10500,int kind=5){auto&v=obj[count];vector[count]=v.o.data();put(v.o.data(),0,(void*)v.vt.data());put(v.vt.data(),0x10,(void*)&objectKind);put(v.o.data(),0x84,kind);put(v.o.data(),0x48,(void*)v.data.data());put(v.data.data(),0x582,uint8_t(king));put(v.data.data(),0x581,uint8_t(tower));put(v.o.data(),0xac,-1);put(v.o.data(),0x78,side);put(v.o.data(),0x7c,x);put(v.o.data(),0x18,(void*)v.parts.data());put(v.o.data(),0x20,4);put(v.o.data(),0x24,4);put(v.parts.data(),16,(void*)v.health.data());put(v.health.data(),8,(void*)v.o.data());put(v.health.data(),0x10,hp);count++;put(objects.data(),0x14,count);}
 void score(int own,int other){assert(result(game.data()));assert(actual_crowns[owner]==own&&actual_crowns[1-owner]==other);}
};
int main(){
 {Fixture f;f.add(0,true,false,7728,9000);f.add(0,false,true,4302,3500);f.add(1,true,false,0,9000);f.score(3,1);}
 {Fixture f;f.add(0,true,false,4180);f.add(0,false,true,2226,7500);f.add(0,false,false,336,17000);f.add(1,true,false,0);f.add(1,false,true,2536,7500);f.add(1,false,false,200,9000,1);f.score(3,1);}
 {Fixture f;f.add(0,true,false,1467);f.add(1,true,false,4417);f.add(1,false,true,728,7500);identity[0]=21;identity[1]=22;f.score(2,1);}
 {Fixture f;assert(!result(f.game.data()));}
 {Fixture f;f.add(0,false,false,100);f.add(1,false,false,100);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(0,true,false,100);f.add(1,true,false,100);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(1,true,false,100);put(f.obj[0].o.data(),0x20,2);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(1,true,false,100);put(f.obj[0].health.data(),8,(void*)nullptr);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(1,true,false,100);for(int i=0;i<4;i++)f.add(0,false,true,100);assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(1,true,false,100);put(f.world.data(),0x1e0,uint8_t(0));assert(!result(f.game.data()));}
 {Fixture f;f.add(0,true,false,100);f.add(1,true,false,100);identity[0]=99;assert(!result(f.game.data()));}
 puts("terminalResultChecks=12");
}
'''
 with tempfile.TemporaryDirectory(prefix='replay-terminal-check-') as directory:
  folder=Path(directory);cpp=folder/'test_result.cpp';binary=folder/'test_result';cpp.write_text(code)
  subprocess.run(['clang++','-std=c++17','-Wall','-Wextra','-Werror',str(cpp),'-o',str(binary)],check=True)
  subprocess.run([str(binary)],check=True)
if __name__=='__main__':main()
