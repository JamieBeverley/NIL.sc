Grain {
	var <>features;
	var <>path;
	var <>buffer;
	classvar <>corpus; // a dictionary w/ reference to different corpuses of units (string -> [Unit])

	*initClass{
		corpus = Dictionary.new();
	}

	*new{
		|features,path,buffer|

		^super.new.init(features, path, buffer);
	}

	init{
		|features,path,buffer|
		this.features = features;
		this.path=path;
		this.buffer=buffer;
	}

	// Not actually euclidean distance for efficiency purposes...
	*euclideanDistance {
		|u,v|
		var r=0;
		if(u.features.keys != v.features.keys,{
			"Cannot calc. euclidean distance".warn;
			(" - u: "++u.features.keys++" - class: ").postln;
			(" - v: "++v.features.keys++" - class: ").postln;
			Error.throw("Keys must macth to determine euclidean difference");
		});
		/*		u.features.keys.do{
		|i|

		r = r+pow((u.features[i])-(v.features[i]),2);
		};
		^ sqrt(r);*/
		u.features.keys.do{
			|i|

			r = r+((u.features[i]-v.features[i]).abs);
		};

		^ r;
	}

	postln{
		this.features.keys.do{
			|i|
			("  - "++i++": "+this.features[i]).postln;
		};
	}

	play{
		|out=0, amp=1, attack = 0.01, release = 0.01|
		{
			var a = PlayBuf.ar(this.buffer.numChannels,bufnum:this.buffer,doneAction:2);
			var env = EnvGen.ar(Env.new([0,1,1,0],[attack,this.buffer.duration-attack-release,release]));
			a = (0!(out))++[a]++(0!(Server.default.options.numOutputBusChannels-1-out));
			Out.ar(~outBus,a*env*amp);
		}.play;
	}

	*midiTest {
		|features, corpusPath="c://Users/jamie/AppData/Local/SuperCollider/Extensions/supercollider-extensions/MRP/supercollider/Units/high-level-features/testing2.json",corpusID|


		var out =0;
		var amp =1;
		var vals = 0!6;
		var dict;
		var dictKeys;
		if(corpusID.isNil,{corpusID=corpusPath});

		MIDIClient.init;
		MIDIIn.connectAll;

		Server.default.waitForBoot({
			Grain.readGrainsFromJSON(corpusPath,corpusID);

			MIDIdef.cc(\concatSynthTest,{
				|val,nm|
				if(nm==7,{
					vals[6]=(val*Server.default.options.numOutputBusChannels/127).floor.clip(0,Server.default.options.numOutputBusChannels-1);},{
					vals[nm-1] = val/127
				});

				Grain.findClosestGrain(Grain(dict),list:Grain.corpus[corpusID]).play(out, amp);

			});// End MIDIdef
		});

	}

	// Starts somewhere random in the corpus, first grain it finds
	// that exists in the tolerance range (a number btwn 0-1) it
	// returns. If nothing in tolerance range, returns closest thing
	*findCloseEnoughGrain{
		|target, list, tolerance = 0.05| //default 5% tolerance
		var offset = list.size.rand;
		var currentClosestGrain = list[0];
		var minDist = target.features.keys.size;
		var tolerantGrain;
		var result;
		tolerance = tolerance * target.features.keys.size; // scale to max distance
		block{
			|break|
			list.size.do{
				|i|
				var dist = Grain.euclideanDistance(list[(i+offset)%list.size],target);
				if(dist<=tolerance,{
					tolerantGrain = list[(i+offset)%list.size];
					break.value(-1);
				},{
					if(dist<minDist,{
						minDist = dist;
						currentClosestGrain = list[(i+offset)%list.size];
					});
				});
			};
		};

		if(tolerantGrain.isNil,{
			tolerantGrain = currentClosestGrain;
			"No grain found in tolerance range..might want to increase tolerance".warn;
		});

		^tolerantGrain;
	}


	*findClosestGrain{
		|target,list|
		var minDistance = Grain.euclideanDistance(target, list[0]);
		var minIndex = 0;
		list.do{
			|v,i|

			var d = Grain.euclideanDistance(target, v);

			if (d<minDistance,{
				minDistance=d;
				minIndex=i
			});
		};
		^list[minIndex];
	}

	*readGrainsFromJSON{
		|path, corpusIDKey|
		var files;
		"Server must be booted for JSON file to be read".warn;
		if(path.keep(-5)!= ".json",
			{
				Error.new("Path must resolve to a .json file").throw;
		});
		if(corpusIDKey.isNil,{
			corpusIDKey = path;
		});
		path.postln;
		files = JSONFileReader.read(path);
		// files = JSONFileReader.read(PathName(path).parentLevelPath(2)++PathName(path).fileName.drop(-5)++"\\";);
		// keys are paths to the soundfiles
		Routine{
			Grain.corpus[corpusIDKey] = [];
			files.keys.do{
				|key,index|

				var i = files[key].collect({|i| i.asFloat}); // dict from "feature"->Value
				var buffer = Buffer.read(Server.default,path:key,action:{
					// ("##### "+key+" "+index).postln;


					Grain.corpus[corpusIDKey] = Grain.corpus[corpusIDKey].add(Grain(features:i,path:key,buffer:buffer));
				});

			};

			"corpus loaded".postln;
		}.play;
	}

	*addCorpus {
		|key, unitList|
		Grain.corpus[key]=unitList;
	}

	*generateGrainSoundFiles{
		|sourcePath, outputFolder, sampleDuration|

		var counter = 0;

		outputFolder.mkdir;
		Server.default.waitForBoot({
			(PathName(sourcePath).entries).do{
				|i|

				i.files.do{
					|j|

					var buffer = Buffer.read(Server.default,j.fullPath,action:{

						buffer.loadToFloatArray(action:{
							|array|
							var chans = buffer.numChannels;
							// Make mono bc meyda doesn't do multichannel
							buffer = Buffer.loadCollection(Server.default, array.unlace(chans).sum*(1/chans),action:{
								|buff|

								(buff.numFrames/Server.default.sampleRate/sampleDuration).floor.do{
									|k|

									// Meyda CLI analysis requires 16bit wav
									buff.write(path:(outputFolder++j.folderName++counter++"_"++k++".wav"),headerFormat:"WAV",sampleFormat:"int16",numFrames:Server.default.sampleRate*sampleDuration,startFrame:	(k*buffer.numFrames/(buffer.numFrames/Server.default.sampleRate/sampleDuration)));

									(outputFolder++j.folderName++counter++"_"++k++".aiff").postln;
									counter=counter+1;
								};
								buff.free;
								buffer.free;


							});// End loadCollections



						});// End loadToFloatArray
					}); // End Buffer.read
				};
			};
		});

		("Finished writing to "+outputFolder).postln;

	}

}
