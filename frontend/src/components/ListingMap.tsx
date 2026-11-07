import type { ReactElement } from "react";
import { CircleMarker, MapContainer, TileLayer, Tooltip } from "react-leaflet";
import "leaflet/dist/leaflet.css";

/**
 * Interaktivní mapa polohy inzerátu. Podklad z OpenStreetMap — bez API klíče.
 * Bez souřadnic ukážeme jen hlášku, protože prodejce zveřejňuje jen lokalitu.
 */
export default function ListingMap({
  lat,
  lon,
  title,
}: {
  lat?: number | null;
  lon?: number | null;
  title: string;
}): ReactElement {
  if (lat == null || lon == null) {
    return (
      <p className="text-sm text-gray-500 bg-gray-100 border border-gray-200 rounded-lg px-4 py-3">
        Prodejce neuvedl přesnou polohu — pouze lokalitu v inzerátu.
      </p>
    );
  }
  return (
    <div className="rounded-xl overflow-hidden border border-gray-200 z-0">
      <MapContainer center={[lat, lon]} zoom={12} scrollWheelZoom={false} className="w-full h-64">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <CircleMarker
          center={[lat, lon]}
          radius={12}
          pathOptions={{ color: "#1a4480", fillColor: "#1a4480", fillOpacity: 0.6 }}
        >
          <Tooltip>{title}</Tooltip>
        </CircleMarker>
      </MapContainer>
      <p className="text-xs text-gray-500 px-3 py-1.5 bg-white">
        Přibližná poloha · podklad © OpenStreetMap
      </p>
    </div>
  );
}