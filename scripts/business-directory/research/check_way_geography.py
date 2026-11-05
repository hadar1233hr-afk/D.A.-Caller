"""Adversarial full-polygon research checks; no real business verification."""
import copy
import math
import unittest

from research_node_geography import RELATIONS
from research_way_geography import ClosedWayScope, complete_ring


def square(x, y, size):
    return [[x, y], [x + size, y], [x + size, y + size], [x, y + size], [x, y]]


def rings(il=None):
    values = {
        "source_IL": il or square(0, 0, 10),
        "Gaza_admin4": square(2, 2, 1),
        "JudeaSamaria_admin4_outer_filled": square(6, 6, 1),
        "WestBank_historic_outer_filled": square(7, 6, 1),
    }
    return {key: [{"coordinates": value}] for key, value in values.items()}


def refs_nodes(coordinates):
    refs = list(range(1, len(coordinates))) + [1]
    nodes = {str(ref): dict(source_id=f"osm:node:{ref}", longitude=lon, latitude=lat,
                           valid_coordinates=True)
             for ref, (lon, lat) in zip(refs[:-1], coordinates[:-1])}
    return refs, nodes


class FullWayGeometryContract(unittest.TestCase):
    def setUp(self):
        self.scope = ClosedWayScope(rings())

    def tearDown(self):
        self.scope.close()

    def classify(self, coordinates, tags=None):
        refs, nodes = refs_nodes(coordinates)
        return self.scope.classify_ring(refs, nodes, tags or {})[0]

    def test_clear_complete_polygon_is_research_only(self):
        refs, nodes = refs_nodes(square(4, 4, 0.1))
        before = copy.deepcopy((refs, nodes))
        decision, coords = self.scope.classify_ring(refs, nodes, {})
        self.assertEqual("research-qualified-complete-source-polygon", decision)
        self.assertEqual(square(4, 4, 0.1), coords)
        self.assertEqual(before, (refs, nodes))

    def test_boundary_sharing_polygon_rejected_even_when_contains_true(self):
        self.assertEqual("reject-near-source-IL-boundary", self.classify(square(0, 4, 0.1)))

    def test_polygon_crossing_il_boundary_rejected(self):
        self.assertEqual("reject-not-wholly-inside-source-IL", self.classify(square(9.95, 4, 0.1)))

    def test_near_il_boundary_rejected_by_angular_margin(self):
        self.assertEqual("reject-near-source-IL-boundary", self.classify(square(0.0005, 4, 0.1)))

    def test_polygon_enclosing_exclusion_with_all_vertices_outside_rejected(self):
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify(square(1.9, 1.9, 1.2)))

    def test_edge_crossing_exclusion_with_vertices_outside_rejected(self):
        coords = [[1.5, 2.4], [3.5, 2.4], [3.5, 2.6], [1.5, 2.6], [1.5, 2.4]]
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify(coords))

    def test_exclusion_tangency_rejected(self):
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify(square(3, 2.5, 0.1)))

    def test_near_outside_exclusion_rejected(self):
        self.assertEqual("reject-intersects-or-near-Gaza_admin4", self.classify(square(3.0005, 2.5, 0.1)))

    def test_both_distinct_westbank_envelopes_excluded(self):
        self.assertEqual("reject-intersects-or-near-JudeaSamaria_admin4_outer_filled", self.classify(square(6.2, 6.2, 0.1)))
        self.assertEqual("reject-intersects-or-near-WestBank_historic_outer_filled", self.classify(square(7.2, 6.2, 0.1)))

    def test_all_vertices_inside_concave_il_does_not_admit_crossing_edge(self):
        # The thin top notch is outside IL; all business vertices avoid it,
        # while the top business edge crosses it. Vertex-only admission is wrong.
        self.scope.close()
        il = [[0, 0], [10, 0], [10, 10], [5.1, 10], [5.1, 5], [4.9, 5],
              [4.9, 10], [0, 10], [0, 0]]
        self.scope = ClosedWayScope(rings(il))
        polygon = [[4.5, 4], [5.5, 4], [5.5, 8], [4.5, 8], [4.5, 4]]
        self.assertTrue(all(self.scope.classify(x, y) == "research-qualified-source-interior"
                            for x, y in polygon))
        self.assertEqual("reject-not-wholly-inside-source-IL", self.classify(polygon))

    def test_open_ref_ring_is_never_closed_by_guessing(self):
        refs, nodes = refs_nodes(square(4, 4, 0.1))
        refs[-1] = 2
        self.assertEqual("reject-open-or-invalid-source-ring", self.scope.classify_ring(refs, nodes, {})[0])

    def test_any_missing_or_mismatched_ref_rejects_whole_polygon(self):
        refs, nodes = refs_nodes(square(4, 4, 0.1))
        del nodes["2"]
        self.assertEqual("reject-missing-or-mismatched-source-node", complete_ring(refs, nodes)[0])
        refs, nodes = refs_nodes(square(4, 4, 0.1))
        nodes["2"]["source_id"] = "osm:node:999"
        self.assertEqual("reject-missing-or-mismatched-source-node", complete_ring(refs, nodes)[0])

    def test_nonfinite_bool_out_of_range_coords_never_enter_geos(self):
        for value in (math.nan, math.inf, True, 181, "4"):
            refs, nodes = refs_nodes(square(4, 4, 0.1))
            nodes["2"]["longitude"] = value
            self.assertEqual("reject-invalid-source-node-coordinate", complete_ring(refs, nodes)[0])

    def test_self_intersection_and_zero_area_rejected(self):
        for coords in ([[4, 4], [4.1, 4.1], [4, 4.1], [4.1, 4], [4, 4]],
                       [[4, 4], [4.1, 4], [4.2, 4], [4, 4]]):
            self.assertEqual("reject-invalid-empty-or-zero-area-source-polygon", self.classify(coords))

    def test_collapsed_source_positions_rejected(self):
        refs = [1, 2, 3, 1]
        nodes = {str(ref): dict(source_id=f"osm:node:{ref}", longitude=4, latitude=4,
                               valid_coordinates=True) for ref in (1, 2, 3)}
        self.assertEqual("reject-collapsed-source-ring", complete_ring(refs, nodes)[0])

    def test_explicit_area_no_is_not_reinterpreted_as_polygon(self):
        self.assertEqual("reject-explicit-area-no", self.classify(square(4, 4, 0.1), {"area": "no"}))


if __name__ == "__main__":
    unittest.main(verbosity=2)
