'use strict';

const React = require('react-native');

const {
  requireNativeComponent,
  PropTypes,
  DeviceEventEmitter,
  NativeModules,
  Dimensions,
} = React;

const { width } = Dimensions.get("window");

const RNGMapsModule = NativeModules.RNGMapsModule;

/* RNGMAPS COMP */
const gmaps = {
  name: 'RNGMapsViewManager',
  propTypes: {
    center: PropTypes.object,
    zoomLevel: PropTypes.number,
    markers: PropTypes.array,
    zoomOnMarkers: PropTypes.bool,
    centerNextLocationFix: PropTypes.bool,
    mapPadding: PropTypes.object,
    showMyLocationButton: PropTypes.bool,

    /* Hackedy hack hack hack */
    scaleX: React.PropTypes.number,
    scaleY: React.PropTypes.number,
    translateX: React.PropTypes.number,
    translateY: React.PropTypes.number,
    rotation: React.PropTypes.number,
  },
};

let MapView = requireNativeComponent('RNGMapsViewManager', gmaps);

class RNGMaps extends React.Component {
  constructor(props) {
    super(props);
    this._event = null;
    this._error = null;
    this._markerClick = null;
    this._onMarkerClick = this._onMarkerClick.bind(this);
    this._onMapChange = this._onMapChange.bind(this);
  }
  static propTypes = {
    onRegionChangeComplete: React.PropTypes.func,
    onMapError: React.PropTypes.func,
  };
  static defaultProps = {
    onRegionChangeComplete: function() {},
    onMapError: function() {},
  };

  componentDidMount() {
    this._event = DeviceEventEmitter.addListener('mapChange',this._onMapChange);

    this._error = DeviceEventEmitter.addListener('mapError', (e: Event) => {
      console.log(`[GMAP_ERROR]: ${e.message}`);
      this.props.onMapError(e);
    });

    this._markerClick = DeviceEventEmitter.addListener('markerClick',this._onMarkerClick);
  }
  componentWillUnmount() {
    this._event && this._event.remove();
    this._error && this._error.remove();
    this._markerClick && this._markerClick.remove();
  }
  componentWillReceiveProps(nextProps) {
  }

  _onMapChange(e) {
    console.log(e);
    this.props.onRegionChangeComplete(e);
  }

  _onMarkerClick(e) {
    const marker = this.props.annotations.find((m) => {
      return e.id == m.id;
    });
    console.log("marker:",marker);
    if (marker && marker.onRightCalloutPress) {
      marker.onRightCalloutPress(e);
    }
  }

  render () {
    let { region, annotations, zoomLevel, ...other } = this.props;

    if (region && region.longitude && region.longitudeDelta) {
      const GLOBE_WIDTH = 256;
      const west = region.longitude - region.longitudeDelta/2;
      const east = region.longitude + region.longitudeDelta/2;
      let angle = east - west;
      if (angle < 0) {
        angle += 360;
      }
      region.zoomLevel = Math.log(width * 360 / angle / GLOBE_WIDTH) / Math.LN2;
    }
    if (region && region.zoomLevel) {
      zoomLevel = undefined;
    }

    return (
      <MapView
        markers={annotations}
        center={region}
        zoomLevel={zoomLevel}
        {...other}
      />
    );
  }
}

module.exports = RNGMaps;
