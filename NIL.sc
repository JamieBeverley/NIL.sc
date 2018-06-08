NIL {

	*out {
		arg degrees,audio;
		var nchnls = Server.default.options.numOutputBusChannels;
		var pos = (degrees+22.5)/180;
		var panned = PanAz.ar(nchnls,audio,pos:pos,orientation:0);
		^Out.ar(0,panned);
	}

	*bootConcatRoulette{
		|device, outChannels=2, corpusJsonPath|
		if(device.isNil.not,{Server.default.options.device = device;});
		Server.default.options.numBuffers = 1024*8;
		Server.default.options.numOutputBusChannels = outChannels;

		Server.default.waitForBoot({
			var cmdPFunc;
			~outBus = Bus.audio(Server.default,Server.default.options.numOutputBusChannels);

			SynthDef(\grain,{
				|amp,out=0,spectralCentroid=0,rms=0,pitch=0,clarity=0,strength=0,turbidity=0,corpus=0,attack=0.01,release=0.01,tolerance=0.05|

				SendReply.kr(Line.kr(-1,1,dur:0.01),"/grain",[corpus,tolerance,out,amp,attack,release,rms,spectralCentroid,pitch,clarity,strength,turbidity]);
				// SendReply.kr(Line.kr(-1,1,dur:0.01),"/grain",0.1!15);
				Out.ar(0,EnvGen.ar(Env.perc(),doneAction:2)*0);
			}).add;

			SynthDef(\out,{
				|lpf=22000, hpf=10, reverb=0,db=0,hrq=1,lrq=1,room=0.3|
				var audio = In.ar(~outBus,2)*(db.dbamp);
				audio = FreeVerb.ar(audio,mix:Clip.kr(reverb,0,1),room:Clip.kr(room,0,1),damp:0.9);
				audio = LPF.ar(audio, Clip.kr(lpf,10,22000));//,1/(resonance.clip(0.0001,1)));
				audio = HPF.ar(audio,Clip.kr(hpf,10,22000));
				audio = Compander.ar(audio,audio,-30.dbamp,slopeAbove:1/2.5,mul:3.dbamp);
				audio = Compander.ar(audio,audio,thresh:-1.dbamp,slopeAbove:1/20); // limiter...
				Out.ar(0,audio);
			}).add;
/*			Grain.readGrainsFromJSON(Platform.userAppSupportDir++"\\Extensions\\SuperCollider-Extensions\\MRP\\supercollider\\Units\\high-level-features\\field-recordings-0.5.json","recordings");


			Grain.readGrainsFromJSON(Platform.userAppSupportDir++"\\Extensions\\SuperCollider-Extensions\\MRP\\supercollider\\Units\\high-level-features\\testing.json","testing");*/
			Grain.readGrainsFromJSON(corpusJsonPath,"c1");


			cmdPFunc = {
				Tdef(\ugh,{
					0.1.wait;
					"Adding new ~out Synth".postln;
					~out = Synth.new(\out,addAction:'addToTail');

					OSCdef(\playGrain,{
						|msg|
						var features = Dictionary.new();
						var corpus = msg[3];
						var tolerance = msg[4];
						var out = msg[5];
						var amp = msg[6];

						var attack = msg[7];
						var release = msg[8];
						var match;

						features["rms"] = msg[9].asFloat;
						features["spectralCentroid"] = msg[10].asFloat;
						features["pitch"] = msg[11].asFloat;
						features["clarity"] = msg[12].asFloat;
						features["strength"] = msg[13].asFloat;
						features["turbidity"] = msg[14].asFloat;
						// match =Grain.findClosestGrain(Grain(features),list:Grain.corpus.asArray[corpus]);
						match =Grain.findCloseEnoughGrain(Grain(features),list:Grain.corpus.asArray[corpus],tolerance:tolerance);
						match.play(out:out,amp:amp,attack:attack,release:release);
					},"/grain",recvPort:NetAddr.langPort).add;
				}).play;
			};

			cmdPFunc.value();
			CmdPeriod.add(cmdPFunc);
		});

	}

}