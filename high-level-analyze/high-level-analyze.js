var args = process.argv.slice(2)
var path = require('path')
var fs = require('fs')

// These 2 should be command line arguments
// var dir = "c://Users/jamie/AppData/Local/SuperCollider/Extensions/supercollider-extensions/MRP/Units/testing";
// var outputFile = "c://Users/jamie/AppData/Local/SuperCollider/Extensions/supercollider-extensions/MRP/Units/testing/features.json";
// var fftSize = 512;

var dir = args[0]
var outputFile = args[1]
if(args[0]==undefined || args[1]==undefined){
  console.log("Required arguments undefined: ")
  console.log("  1 - directory containing json")
  console.log("  2 - path to file to write to (ending in .json)")
  process.exit(1)
}
var fftSize = 512;

var featureMap = {}

fs.readdir(dir, function(err, files){
  if(err){
    console.log('ERROR:   '+err)
    process.exit(1);
  }

  for( i in files){
    if (files[i].endsWith('.json')){
        try{
          var slash = dir.endsWith("/")?"":"/"
          var contents = fs.readFileSync(dir+slash+files[i]);
          var a = JSON.parse(contents)
          // featureMap[path.normalize(__dirname)"/"dir+slash+files[i].slice(0,-4)+"wav"] = calculateFeatures(a)
          featureMap[path.resolve(dir+slash+files[i].slice(0,-4))+"wav"] = calculateFeatures(a)
          // you want the absolute path of a file then you can also use resolve function of path module
          // var soemPath = "./img.jpg";
          // var resolvedPath = path.resolve(soemPath);
          // console.log(resolvedPath);

          // console.log(files[i])
          // console.log(a['spectralCentroid'])
        }catch(e){
          console.log("Erro with file: "+files[i])
          console.log(e)
        }
    }
  }


  fs.writeFile(outputFile, JSON.stringify(featureMap), 'utf8', function (err) {
      if (err) {
          return console.log(err);
      }
      console.log("File saved to "+outputFile);
  });

})

function calculateFeatures (features){
  var r = {
    pitch:calculatePitch(features),
    turbidity:calculateTurbidity(features),
    clarity: calculateClarity(features),
    strength: calculateStrength(features),
    spectralCentroid: mean(features['spectralCentroid'])/(fftSize/2),
    rms: mean(features['rms'])
  }
  return r
}


// This should really be in a module used both by client.js and this...


function calculatePitch(features, nonGraphables){
  // var m = mean(features['loudness'].specific)
  // var maximum = max(features['loudness'].specific)
  var amplitudeSpectrum = features['amplitudeSpectrum']

  var m;
  var maximum;
  var length = 0;
  if(amplitudeSpectrum[0]){
    m = 0;
    maximum = amplitudeSpectrum[0][0]

    for (i in amplitudeSpectrum){

      for (j in amplitudeSpectrum[i]){
        m += amplitudeSpectrum[i][j]
        length++

        if (amplitudeSpectrum[i][j]>maximum){maximum = amplitudeSpectrum[i][j]}
      }

      // localMax = max(amplitudeSpectrum)
      // m+=mean(amplitudeSpectrum[i])
      // console.log(i)
    }
    // m = m / amplitudeSpectrum.length
    // console.log("l: "+length)

    m = m /length;
    // console.log("maximum: "+maximum)
  } else {
    m = mean(features['amplitudeSpectrum'])
    maximum = max(features['amplitudeSpectrum'])
  }

  // TODO - factor in spectral flatness
  var r = clip((maximum/m)/128)
  return r
}


// TODO - peak/gust detections
function calculateTurbidity(features, nonGraphables){
  // var centroid = nonGraphables['spectralCentroid']
  // var centroidComponent = centroid.long.variance
  var spectralTurbidity, powerTurbidity;

  var centroid, rms;

  if(nonGraphables==undefined){
      var cMean = mean(features['spectralCentroid'])
      var cStdDev = stdDev(features['spectralCentroid'])
      centroid = createUniformWindowedObj(cMean,cStdDev)
      var rMean = mean(features['rms'])
      var rStdDev = stdDev(features['rms'])
      rms = createUniformWindowedObj(rMean,rStdDev)
  } else{
    centroid = nonGraphables['spectralCentroid'].windowedValues;
    rms = nonGraphables['rms'].windowedValues;
  }

  spectralTurbidity = clip(scaleSpectralTurbidity((centroid.long.stdDev/3+centroid.medium.stdDev/3+centroid.short.stdDev/3)/20),0,1);
  powerTurbidity = Math.sqrt((rms.long.stdDev/rms.long.mean/3+rms.medium.stdDev/rms.medium.mean/3+rms.short.stdDev/rms.short.mean/3)/1.05);
  // powerTurbidity = rms.long.stdDev/rms.long.mean/0.7/3 //0.7 just a scaling thing - empirically found that the max it would ever produce is 0.7
  // powerTurbidity = powerTurbidity + rms.medium.stdDev/rms.medium.mean/0.7/3;
  // powerTurbidity = clip(powerTurbidity+rms.short.stdDev/rms.short.mean/0.7/3,0,1);

  var r = 0.6*powerTurbidity+0.4*spectralTurbidity;

  return r

}



// Lower frequency+higher volume -> greater 'strength'
function calculateStrength(features, nonGraphables){
  var centroid, loudnessTotal;
  if(nonGraphables){
    // how to normalize this accross devices - some will be recording louder than others?
    // calibration period?
    loudnessTotal = nonGraphables['loudnessTotal'].windowedValues
    // var loudness = clip(Math.sqrt((loudnessTotal.long.mean+loudnessTotal.medium.mean+loudnessTotal.short.mean)/3/50),0,1)
    centroid = nonGraphables['spectralCentroid'].windowedValues;
  } else {
    var lt = []
    for (i in features['loudness']){
      lt.push(features['loudness'][i].total)
    }
    loudnessTotal = createUniformWindowedObj(mean(lt),stdDev(lt))
    centroid = createUniformWindowedObj(mean(features['spectralCentroid']),stdDev(features['spectralCentroid']))
  }

  loudness = clip((loudnessTotal.long.mean+loudnessTotal.medium.mean+loudnessTotal.short.mean)/3/50,0,1)
  var normalizedCentroid = clip(scaleCentroidStrength(((centroid.long.mean+centroid.medium.mean+centroid.short.mean)/3)/(fftSize/2)),0,1)
  // centroid = clip(scaleCentroidStrength(normalizedCentroid),0,1);
  return normalizedCentroid*0.2+0.8*loudness;
}


function calculateClarity(features, nonGraphables){
  var centroid, spectralFlatness, rms

  if (nonGraphables){
    centroid = nonGraphables['spectralCentroid'].windowedValues
    flatness = nonGraphables['spectralFlatness'].windowedValues;
    rms = nonGraphables['rms'].windowedValues
  } else {
    centroid = createUniformWindowedObj(mean(features['spectralCentroid']), stdDev(features['spectralCentroid']))
    rms = createUniformWindowedObj(mean(features['rms']),stdDev(features['rms']))
    flatness = createUniformWindowedObj(mean(features['spectralFlatness']), stdDev(features['spectralFlatness']))
  }


  var short = scaleSpectralFlatness(flatness.short.mean)
  var long = scaleSpectralFlatness(flatness.long.mean)
  var medium = scaleSpectralFlatness(flatness.medium.mean)
  var lowness = Math.sqrt((centroid.long.mean+centroid.medium.mean+centroid.short.mean)/3/(fftSize/2))
  var spectralClarity = (1-clip((long+short+medium)/3))*lowness


  // var spectralClarity;
  // var centroid = nonGraphables['spectralCentroid'].windowedValues;
  // var rms = nonGraphables['rms'].windowedValues;
  // spectralClarity = 1-clip(scaleSpectralTurbidity((centroid.long.stdDev/3+centroid.medium.stdDev/3+centroid.short.stdDev/3)/20),0,1);

  var levelClarity;
  levelClarity = 1-clip(Math.sqrt((rms.long.stdDev/rms.long.mean/3+rms.medium.stdDev/rms.medium.mean/3+rms.short.stdDev/rms.short.mean/3)/1.05));
  var r = spectralClarity*0.7+levelClarity*0.3;

  return r


}

function scaleSpectralFlatness(x){
  return (1/(1+Math.pow(Math.E,(-10)*x+5)))
}

function scaleCentroidStrength(x){
  return 1/(1+Math.pow(Math.E,(-10)*x+5))
}

// crudely mapped sigmoid...
function scaleSpectralTurbidity(x){
  return 1/(1+Math.pow(Math.E,-10*clip(x,0,1)+5))
}


function createUniformWindowedObj(mean,stdDev){
  return {long:{mean:mean,stdDev:stdDev},medium:{mean:mean,stdDev:stdDev},short:{mean:mean,stdDev:stdDev}}
}



// Utilities....

function mean (list){
  var x = 0;
  for (i in list){
    x+=list[i]
  }
  var len;
  // console.log(typeof(l))
  if(typeof(list) == 'object'){
    len = Object.keys(list).length
  } else {
    // console.log(typeof(list))
    len = list.length
  }
  return x/len
}

function stdDev (l){
  var m = mean(l)
  var x=0;
  for (i in l){
    x += Math.pow(l[i]-m,2);
  }
  return Math.sqrt(x/(l.length-1))
}

function max (l){
  var m = l[0]
  for (i in l){
    if (l[i]>m){
      m=l[i]
    }
  }
  return m
}

function clip(v,low,high){
  if(low==undefined){low=0}
  if(high==undefined){high=1}
  return Math.min(high,Math.max(low,v))
}
